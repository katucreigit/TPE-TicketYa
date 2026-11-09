package com.ticketya.reservas.service;

import com.ticketya.reservas.dto.AsientoRef;
import com.ticketya.reservas.dto.DevolucionRequest;
import com.ticketya.reservas.dto.ReservaRequest;
import com.ticketya.reservas.dto.ReservaResponse;
import com.ticketya.reservas.exception.CodigoError;
import com.ticketya.reservas.exception.ConflictException;
import com.ticketya.reservas.model.InventarioSector;
import com.ticketya.reservas.model.Reserva;
import com.ticketya.reservas.model.ReservaItem;
import com.ticketya.reservas.model.enums.EstadoReserva;
import com.ticketya.reservas.model.enums.TipoSector;
import com.ticketya.reservas.repository.AsientoRepository;
import com.ticketya.reservas.repository.InventarioEventoRepository;
import com.ticketya.reservas.repository.InventarioSectorRepository;
import com.ticketya.reservas.repository.ReservaItemRepository;
import com.ticketya.reservas.repository.ReservaRepository;
import com.ticketya.reservas.repository.projection.InventarioReservaView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reservas temporales: creacion, consulta, cancelacion, confirmacion, liberacion, devolucion y vencimiento.
 * <p>
 * Toda la concurrencia se resuelve en PostgreSQL: advisory lock por cliente y evento para el limite,
 * UPDATE condicionales por estado para los lugares y transiciones de la reserva, y FOR UPDATE SKIP LOCKED
 * en el vencimiento. Las fechas salen siempre de la base (localtimestamp), nunca del reloj de la JVM.
 */
@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final ReservaItemRepository reservaItemRepository;
    private final AsientoRepository asientoRepository;
    private final InventarioEventoRepository inventarioEventoRepository;
    private final InventarioSectorRepository inventarioSectorRepository;
    private final AvisoAgotadoService avisoAgotadoService;
    private final int minutosExpiracion;
    private final int loteVencimiento;

    public ReservaService(ReservaRepository reservaRepository,
                          ReservaItemRepository reservaItemRepository,
                          AsientoRepository asientoRepository,
                          InventarioEventoRepository inventarioEventoRepository,
                          InventarioSectorRepository inventarioSectorRepository,
                          AvisoAgotadoService avisoAgotadoService,
                          @Value("${ticketya.reserva.minutos-expiracion:10}") int minutosExpiracion,
                          @Value("${ticketya.jobs.lote-vencimiento:100}") int loteVencimiento) {
        this.reservaRepository = reservaRepository;
        this.reservaItemRepository = reservaItemRepository;
        this.asientoRepository = asientoRepository;
        this.inventarioEventoRepository = inventarioEventoRepository;
        this.inventarioSectorRepository = inventarioSectorRepository;
        this.avisoAgotadoService = avisoAgotadoService;
        this.minutosExpiracion = minutosExpiracion;
        this.loteVencimiento = loteVencimiento;
    }

    // ------------------------------------------------------------------ creacion

    /**
     * Crea una reserva PENDIENTE, todo o nada, en una transaccion con este orden:
     * (1) advisory lock del cliente y evento, (2) inventario con FOR SHARE y abierto, (3) limite por cliente,
     * (4) UPDATE condicionales de asientos ordenados por fila y numero, o resta atomica del cupo.
     * Cualquier excepcion revierte la transaccion completa: no queda ningun lugar tomado.
     */
    @Transactional
    public ReservaResponse crear(String clienteId, ReservaRequest request) {
        boolean pideAsientos = validarPedido(request);
        Long eventoId = request.eventoId();
        Long sectorId = request.sectorId();

        // (1) Serializa las reservas del mismo cliente en el mismo evento hasta el commit.
        reservaRepository.bloquearClienteEvento(eventoId, clienteId);

        // (2) Inventario con FOR SHARE: un cierre concurrente espera a esta reserva.
        InventarioReservaView inventario = inventarioEventoRepository.bloquearParaReservar(eventoId)
                .orElseThrow(() -> CodigoError.noEncontrado("El evento " + eventoId + " no tiene inventario"));
        if (!Boolean.TRUE.equals(inventario.getAbierto())) {
            throw CodigoError.eventoNoDisponible();
        }
        InventarioSector sector = inventarioSectorRepository.findByInventarioEventoEventoIdAndSectorId(eventoId, sectorId)
                .orElseThrow(() -> CodigoError.noEncontrado(
                        "El sector " + sectorId + " no existe en el inventario del evento " + eventoId));
        validarTipoDeSector(sector.getTipo(), pideAsientos);
        int pedidos = pideAsientos ? request.asientos().size() : request.cantidad();

        // (3) Limite por cliente: PENDIENTE no vencidas y CONFIRMADA (sin unidades devueltas).
        long contadas = reservaRepository.unidadesContadas(eventoId, clienteId);
        if (contadas + pedidos > inventario.getMaxPorCliente()) {
            throw CodigoError.limitePorCliente(inventario.getMaxPorCliente(), contadas);
        }

        // (4) Reservar. Una sola lectura de la hora de la base para creadaEn y expiraEn.
        LocalDateTime ahora = reservaRepository.ahora();
        Reserva reserva = new Reserva();
        reserva.setEventoId(eventoId);
        reserva.setSectorId(sectorId);
        reserva.setClienteId(clienteId);
        reserva.setEstado(EstadoReserva.PENDIENTE);
        reserva.setCantidad(pedidos);
        reserva.setCreadaEn(ahora);
        reserva.setExpiraEn(ahora.plusMinutes(minutosExpiracion));
        reserva = reservaRepository.save(reserva);

        List<AsientoRef> items = pideAsientos
                ? reservarAsientos(reserva, request.asientos())
                : reservarCupo(reserva, request.cantidad());

        avisoAgotadoService.evaluarDespuesDelCommit(eventoId);
        return respuesta(reserva, items);
    }

    /**
     * UPDATE condicional por asiento, siempre ordenados por fila y numero para que dos reservas con asientos
     * en comun tomen los locks en el mismo orden y no se bloqueen mutuamente. Si alguno afecta 0 filas se lanza
     * la excepcion y se revierte todo.
     */
    private List<AsientoRef> reservarAsientos(Reserva reserva, List<AsientoRef> pedidos) {
        List<AsientoRef> ordenados = pedidos.stream().sorted(AsientoRef.POR_FILA_Y_NUMERO).toList();
        List<AsientoRef> fallidos = new ArrayList<>();
        for (AsientoRef a : ordenados) {
            int filas = asientoRepository.reservar(reserva.getEventoId(), reserva.getSectorId(),
                    a.fila(), a.numero(), reserva.getId());
            if (filas == 0) {
                fallidos.add(a);
            }
        }
        if (!fallidos.isEmpty()) {
            List<String> inexistentes = fallidos.stream()
                    .filter(a -> !asientoRepository.existsByEventoIdAndSectorIdAndFilaAndNumero(
                            reserva.getEventoId(), reserva.getSectorId(), a.fila(), a.numero()))
                    .map(a -> CodigoError.asiento(a.fila(), a.numero()))
                    .toList();
            if (!inexistentes.isEmpty()) {
                throw CodigoError.validacion("Hay asientos que no existen en el sector", inexistentes);
            }
            throw CodigoError.asientoNoDisponible(
                    fallidos.stream().map(a -> CodigoError.asiento(a.fila(), a.numero())).toList());
        }
        for (Long asientoId : asientoRepository.idsDeReserva(reserva.getId())) {
            ReservaItem item = new ReservaItem();
            item.setAsiento(asientoRepository.getReferenceById(asientoId));
            item.setCantidad(1);
            reserva.agregarItem(item);
        }
        return ordenados;
    }

    /** Resta atomica con disponibles >= n; si afecta 0 filas no hay cupo y se revierte todo. */
    private List<AsientoRef> reservarCupo(Reserva reserva, int cantidad) {
        if (inventarioSectorRepository.descontarCupo(reserva.getEventoId(), reserva.getSectorId(), cantidad) == 0) {
            throw CodigoError.cupoInsuficiente();
        }
        ReservaItem item = new ReservaItem();
        item.setCantidad(cantidad);
        reserva.agregarItem(item);
        return List.of();
    }

    /** Validaciones que no dependen de la base. Devuelve true si el pedido trae asientos. */
    private static boolean validarPedido(ReservaRequest request) {
        boolean conAsientos = request.asientos() != null && !request.asientos().isEmpty();
        boolean conCantidad = request.cantidad() != null;
        if (conAsientos == conCantidad) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("Debe enviarse asientos (sector numerado) o cantidad (sector no numerado), no ambos"));
        }
        if (conCantidad && request.cantidad() <= 0) {
            throw CodigoError.validacion("Datos invalidos", List.of("cantidad debe ser mayor a 0"));
        }
        if (conAsientos) {
            validarSinRepetidos(request.asientos(), "asientos");
        }
        return conAsientos;
    }

    private static void validarTipoDeSector(TipoSector tipo, boolean conAsientos) {
        if (tipo == TipoSector.NUMERADO && !conAsientos) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("El sector es numerado: se deben enviar asientos, no cantidad"));
        }
        if (tipo == TipoSector.NO_NUMERADO && conAsientos) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("El sector no es numerado: se debe enviar cantidad, no asientos"));
        }
    }

    private static void validarSinRepetidos(List<AsientoRef> asientos, String campo) {
        Set<AsientoRef> vistos = new HashSet<>();
        List<String> repetidos = asientos.stream()
                .filter(a -> !vistos.add(a))
                .map(a -> campo + " repite el asiento " + CodigoError.asiento(a.fila(), a.numero()))
                .distinct()
                .toList();
        if (!repetidos.isEmpty()) {
            throw CodigoError.validacion("Datos invalidos", repetidos);
        }
    }

    // ------------------------------------------------------------------ consulta y cancelacion

    @Transactional(readOnly = true)
    public ReservaResponse obtener(String clienteId, UUID id) {
        Reserva reserva = buscarDelCliente(clienteId, id);
        List<AsientoRef> items = reservaItemRepository.lugaresVigentes(id).stream()
                .map(l -> new AsientoRef(l.getFila(), l.getNumero()))
                .toList();
        return respuesta(reserva, items);
    }

    /**
     * Cancelacion del cliente: PENDIENTE pasa a LIBERADA y sus lugares vuelven. Ya LIBERADA o VENCIDA no
     * modifica nada (204). CONFIRMADA responde 409 RESERVA_CONFIRMADA.
     */
    @Transactional
    public void cancelar(String clienteId, UUID id) {
        Reserva reserva = buscarDelCliente(clienteId, id);
        if (liberarSiPendiente(reserva.getId(), reserva.getEventoId())) {
            return;
        }
        if (estadoActual(id) == EstadoReserva.CONFIRMADA) {
            throw CodigoError.reservaConfirmada();
        }
    }

    private Reserva buscarDelCliente(String clienteId, UUID id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> CodigoError.noEncontrado("La reserva " + id + " no existe"));
        if (!reserva.getClienteId().equals(clienteId)) {
            throw CodigoError.accesoDenegado();
        }
        return reserva;
    }

    // ------------------------------------------------------------------ internos (ms-ventas)

    /**
     * Confirma una reserva PENDIENTE vigente y vende sus asientos. Excluyente con el vencimiento: el UPDATE
     * solo aplica si sigue PENDIENTE y expira_en > localtimestamp. Ya CONFIRMADA responde 200 sin cambios.
     */
    @Transactional
    public void confirmar(UUID id) {
        if (reservaRepository.confirmar(id) == 1) {
            asientoRepository.venderDeReserva(id);
            return;
        }
        switch (estadoActual(id)) {
            case CONFIRMADA -> {
                // Confirmacion repetida: sin cambios.
            }
            case LIBERADA -> throw CodigoError.estadoInvalido("La reserva " + id + " fue liberada");
            // VENCIDA, o PENDIENTE ya expirada que el job todavia no proceso.
            case VENCIDA, PENDIENTE -> throw CodigoError.reservaVencida();
        }
    }

    /**
     * Liberacion por compensacion: PENDIENTE pasa a LIBERADA y sus lugares vuelven. Ya LIBERADA o VENCIDA
     * responde 200 sin cambios; CONFIRMADA, 409.
     */
    @Transactional
    public void liberar(UUID id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> CodigoError.noEncontrado("La reserva " + id + " no existe"));
        if (liberarSiPendiente(id, reserva.getEventoId())) {
            return;
        }
        if (estadoActual(id) == EstadoReserva.CONFIRMADA) {
            throw CodigoError.reservaConfirmada();
        }
    }

    /**
     * Devolucion aprobada sobre una reserva CONFIRMADA. Numerados: cada asiento pasa a LIBRE, su item queda
     * con cantidad 0 y se descuenta de la reserva; un asiento ya devuelto se ignora y uno ajeno responde 409.
     * No numerados: se suma al cupo y se descuenta de la reserva sin superar lo comprado.
     */
    @Transactional
    public void devolver(UUID id, DevolucionRequest request) {
        // Lock de la reserva: serializa devoluciones simultaneas sobre la misma reserva.
        EstadoReserva estado = reservaRepository.bloquearEstado(id)
                .map(EstadoReserva::valueOf)
                .orElseThrow(() -> CodigoError.noEncontrado("La reserva " + id + " no existe"));
        if (estado != EstadoReserva.CONFIRMADA) {
            throw CodigoError.estadoInvalido("Solo se pueden devolver lugares de una reserva confirmada");
        }
        Reserva reserva = reservaRepository.findById(id).orElseThrow();
        TipoSector tipo = inventarioSectorRepository
                .findByInventarioEventoEventoIdAndSectorId(reserva.getEventoId(), reserva.getSectorId())
                .map(InventarioSector::getTipo)
                .orElseThrow(() -> CodigoError.noEncontrado("El sector de la reserva no tiene inventario"));

        boolean conItems = request.items() != null && !request.items().isEmpty();
        boolean conCantidad = request.cantidad() != null;
        if (conItems == conCantidad) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("Debe enviarse items (sector numerado) o cantidad (sector no numerado), no ambos"));
        }
        validarTipoDeSector(tipo, conItems);

        boolean cambio = conItems
                ? devolverAsientos(reserva, request.items())
                : devolverCupo(reserva, request.cantidad());
        if (cambio) {
            avisoAgotadoService.evaluarDespuesDelCommit(reserva.getEventoId());
        }
    }

    private boolean devolverAsientos(Reserva reserva, List<AsientoRef> items) {
        validarSinRepetidos(items, "items");
        UUID id = reserva.getId();
        List<AsientoRef> ordenados = items.stream().sorted(AsientoRef.POR_FILA_Y_NUMERO).toList();
        List<String> ajenos = new ArrayList<>();
        int devueltos = 0;
        for (AsientoRef a : ordenados) {
            Long asientoId = asientoRepository
                    .idDeLugar(reserva.getEventoId(), reserva.getSectorId(), a.fila(), a.numero())
                    .orElse(null);
            if (asientoId == null) {
                ajenos.add(CodigoError.asiento(a.fila(), a.numero()));
            } else if (reservaItemRepository.marcarAsientoDevuelto(id, asientoId) == 1) {
                if (asientoRepository.liberarVendido(asientoId, id) == 0) {
                    throw CodigoError.estadoInvalido("El asiento " + CodigoError.asiento(a.fila(), a.numero())
                            + " no figura vendido para la reserva");
                }
                devueltos++;
            } else if (!reservaItemRepository.asientoPerteneceAReserva(id, asientoId)) {
                ajenos.add(CodigoError.asiento(a.fila(), a.numero()));
            }
            // Si pertenece y ya estaba devuelto: repeticion, sin cambios.
        }
        if (!ajenos.isEmpty()) {
            throw new ConflictException(CodigoError.ESTADO_INVALIDO,
                    "Los asientos " + String.join(", ", ajenos) + " no pertenecen a la reserva", ajenos);
        }
        if (devueltos > 0 && reservaRepository.descontarCantidad(id, devueltos) == 0) {
            throw CodigoError.estadoInvalido("La devolucion supera lo comprado en la reserva");
        }
        return devueltos > 0;
    }

    private boolean devolverCupo(Reserva reserva, int cantidad) {
        if (cantidad <= 0) {
            throw CodigoError.validacion("Datos invalidos", List.of("cantidad debe ser mayor a 0"));
        }
        UUID id = reserva.getId();
        if (reservaRepository.descontarCantidad(id, cantidad) == 0
                || reservaItemRepository.descontarCupoDevuelto(id, cantidad) == 0) {
            throw CodigoError.estadoInvalido("La devolucion supera lo comprado en la reserva");
        }
        if (inventarioSectorRepository.sumarCupo(reserva.getEventoId(), reserva.getSectorId(), cantidad) == 0) {
            throw CodigoError.estadoInvalido("La devolucion superaria la capacidad del sector");
        }
        return true;
    }

    // ------------------------------------------------------------------ vencimiento

    /**
     * Vence un lote de reservas PENDIENTE expiradas en una transaccion: las bloquea con FOR UPDATE SKIP LOCKED
     * (otra instancia no procesa las mismas), las pasa a VENCIDA, devuelve sus asientos a LIBRE y suma de nuevo
     * el cupo de los sectores no numerados. Devuelve true si el lote salio completo y puede haber mas.
     */
    @Transactional
    public boolean vencerLote() {
        List<UUID> ids = reservaRepository.bloquearVencidas(loteVencimiento);
        if (ids.isEmpty()) {
            return false;
        }
        int vencidas = reservaRepository.vencer(ids);
        if (vencidas != ids.size()) {
            // Las filas estan bloqueadas por esta transaccion y se leyeron con el mismo localtimestamp.
            throw new IllegalStateException("Se esperaban " + ids.size() + " reservas vencidas y se vencieron " + vencidas);
        }
        asientoRepository.liberarDeReservas(ids);
        inventarioSectorRepository.devolverCupoDeReservas(ids);
        return ids.size() == loteVencimiento;
    }

    // ------------------------------------------------------------------ auxiliares

    /** UPDATE condicional PENDIENTE -> LIBERADA; solo si aplico se devuelven los lugares. */
    private boolean liberarSiPendiente(UUID id, Long eventoId) {
        List<UUID> ids = List.of(id);
        if (reservaRepository.liberar(ids) == 0) {
            return false;
        }
        asientoRepository.liberarDeReservas(ids);
        inventarioSectorRepository.devolverCupoDeReservas(ids);
        avisoAgotadoService.evaluarDespuesDelCommit(eventoId);
        return true;
    }

    /** Estado leido de la base despues de un UPDATE condicional que no aplico. */
    private EstadoReserva estadoActual(UUID id) {
        return reservaRepository.estadoActual(id)
                .map(EstadoReserva::valueOf)
                .orElseThrow(() -> CodigoError.noEncontrado("La reserva " + id + " no existe"));
    }

    private static ReservaResponse respuesta(Reserva reserva, List<AsientoRef> items) {
        return new ReservaResponse(reserva.getId(), reserva.getEventoId(), reserva.getSectorId(),
                reserva.getClienteId(), reserva.getEstado(), reserva.getCantidad(), items, reserva.getExpiraEn());
    }
}
