package com.ticketya.reservas.service;

import com.ticketya.reservas.client.FilaClient;
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
import com.ticketya.reservas.trazabilidad.Correlacion;
import com.ticketya.reservas.trazabilidad.RegistroOperaciones;
import com.ticketya.reservas.trazabilidad.RegistroOperaciones.MotivoLiberacion;
import com.ticketya.reservas.trazabilidad.RegistroOperaciones.Operacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ticketya.reservas.trazabilidad.RegistroOperaciones.datos;

/**
 * Reservas temporales: creacion, consulta, cancelacion, confirmacion, liberacion, devolucion y vencimiento.
 * <p>
 * Toda la concurrencia se resuelve en PostgreSQL: advisory lock por cliente y evento para el limite,
 * UPDATE condicionales por estado para los lugares y transiciones de la reserva, y FOR UPDATE SKIP LOCKED
 * en el vencimiento. Las fechas salen siempre de la base (localtimestamp), nunca del reloj de la JVM.
 * <p>
 * Las operaciones que cambian los lugares libres (crear, cancelar, liberar, devolver) abren su transaccion con
 * {@link TransactionTemplate} y evaluan el aviso de agotado recien al salir de ella. El aviso abre otra
 * transaccion: si se evaluara en afterCommit, cada solicitud retendria su conexion mientras pide una segunda, y
 * una rafaga de reservas igual al tamano del pool lo agotaria (todas esperando hasta el timeout de Hikari).
 * Si hay una transaccion externa, {@code evaluarDespuesDelCommit} igual espera a su commit.
 */
@Service
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);

    private final ReservaRepository reservaRepository;
    private final ReservaItemRepository reservaItemRepository;
    private final AsientoRepository asientoRepository;
    private final InventarioEventoRepository inventarioEventoRepository;
    private final InventarioSectorRepository inventarioSectorRepository;
    private final AvisoAgotadoService avisoAgotadoService;
    private final int minutosExpiracion;
    private final int loteVencimiento;
    private final TransactionTemplate transaccion;
    private final RegistroOperaciones registro;
    private final FilaClient filaClient;
    private final boolean consultarFila;

    public ReservaService(ReservaRepository reservaRepository,
                          ReservaItemRepository reservaItemRepository,
                          AsientoRepository asientoRepository,
                          InventarioEventoRepository inventarioEventoRepository,
                          InventarioSectorRepository inventarioSectorRepository,
                          AvisoAgotadoService avisoAgotadoService,
                          PlatformTransactionManager transactionManager,
                          RegistroOperaciones registro,
                          FilaClient filaClient,
                          @Value("${ticketya.reserva.minutos-expiracion:10}") int minutosExpiracion,
                          @Value("${ticketya.jobs.lote-vencimiento:100}") int loteVencimiento,
                          @Value("${ticketya.fila.consultar:true}") boolean consultarFila) {
        this.transaccion = new TransactionTemplate(transactionManager);
        this.registro = registro;
        this.filaClient = filaClient;
        this.consultarFila = consultarFila;
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
    public ReservaResponse crear(String clienteId, ReservaRequest request) {
        // add-fila-virtual: un pedido invalido responde 400 antes de consultar la fila. validarPedido es pura y se
        // vuelve a ejecutar dentro de la transaccion, donde se usa su resultado.
        ReglasReserva.validarPedido(request);
        verificarFila(request.eventoId(), clienteId);
        ReservaResponse respuesta = transaccion.execute(s -> crearEnTransaccion(clienteId, request));
        registro.registrar(Operacion.RESERVA_CREADA, datos(
                "reservaId", respuesta.reservaId(),
                "eventoId", respuesta.eventoId(),
                "sectorId", respuesta.sectorId(),
                "clienteId", respuesta.clienteId(),
                "cantidad", respuesta.cantidad(),
                "asientos", asientos(respuesta.items()),
                "expiraEn", respuesta.expiraEn()));
        avisoAgotadoService.evaluarDespuesDelCommit(request.eventoId());
        return respuesta;
    }

    /**
     * Fila virtual (add-fila-virtual, design.md D10): fuera de la transaccion, para no retener una conexion de la
     * base durante la llamada HTTP. Si ms-fila no responde se deja pasar (fail-open) y se registra una advertencia:
     * la fila ordena el acceso, pero la integridad de la venta sigue garantizada por esta base.
     */
    private void verificarFila(Long eventoId, String clienteId) {
        if (!consultarFila) {
            return;
        }
        FilaClient.Estado estado = filaClient.estado(eventoId, clienteId);
        if (!estado.disponible()) {
            log.warn("FILA_NO_DISPONIBLE eventoId={} clienteId={} causa={} correlationId={}",
                    eventoId, Correlacion.limpiar(clienteId), Correlacion.limpiar(estado.causa()),
                    Correlacion.actual());
            return;
        }
        if (estado.bloquea()) {
            throw CodigoError.filaNoHabilitado();
        }
    }

    private ReservaResponse crearEnTransaccion(String clienteId, ReservaRequest request) {
        boolean pideAsientos = ReglasReserva.validarPedido(request);
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
        ReglasReserva.validarTipoDeSector(sector.getTipo(), pideAsientos);
        int pedidos = pideAsientos ? request.asientos().size() : request.cantidad();

        // (3) Limite por cliente: PENDIENTE no vencidas y CONFIRMADA (sin unidades devueltas).
        long contadas = reservaRepository.unidadesContadas(eventoId, clienteId);
        ReglasReserva.verificarLimite(contadas, pedidos, inventario.getMaxPorCliente());

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
        return respuesta(reserva, items);
    }

    /**
     * UPDATE condicional por asiento, siempre ordenados por fila y numero para que dos reservas con asientos
     * en comun tomen los locks en el mismo orden y no se bloqueen mutuamente. Si alguno afecta 0 filas se lanza
     * la excepcion y se revierte todo.
     */
    private List<AsientoRef> reservarAsientos(Reserva reserva, List<AsientoRef> pedidos) {
        List<AsientoRef> ordenados = ReglasReserva.ordenados(pedidos);
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

    // ------------------------------------------------------------------ consulta y cancelacion

    /**
     * Una PENDIENTE ya expirada segun la base (expira_en <= localtimestamp) se informa como VENCIDA aunque el job
     * todavia no la haya procesado, para que ms-ventas decida por estado y no compare relojes.
     */
    @Transactional(readOnly = true)
    public ReservaResponse obtener(String clienteId, UUID id) {
        Reserva reserva = buscarDelCliente(clienteId, id);
        List<AsientoRef> items = reservaItemRepository.lugaresVigentes(id).stream()
                .map(l -> new AsientoRef(l.getFila(), l.getNumero()))
                .toList();
        EstadoReserva estado = reserva.getEstado() == EstadoReserva.PENDIENTE && reservaRepository.expirada(id)
                ? EstadoReserva.VENCIDA
                : reserva.getEstado();
        return respuesta(reserva, estado, items);
    }

    /**
     * Cancelacion del cliente: PENDIENTE pasa a LIBERADA y sus lugares vuelven. Ya LIBERADA o VENCIDA no
     * modifica nada (204). CONFIRMADA responde 409 RESERVA_CONFIRMADA.
     */
    public void cancelar(String clienteId, UUID id) {
        Reserva liberada = transaccion.execute(s -> {
            Reserva reserva = buscarDelCliente(clienteId, id);
            return liberarPendienteOFallarSiConfirmada(reserva) != null ? reserva : null;
        });
        registrarLiberada(liberada, MotivoLiberacion.CANCELACION_CLIENTE);
        avisarSiCambio(liberada == null ? null : liberada.getEventoId());
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
            // Se escribe despues del commit: si la transaccion se revierte, no queda registrada.
            reservaRepository.findById(id).ifPresent(r -> registro.registrar(Operacion.RESERVA_CONFIRMADA, datos(
                    "reservaId", id,
                    "eventoId", r.getEventoId(),
                    "sectorId", r.getSectorId(),
                    "clienteId", r.getClienteId(),
                    "cantidad", r.getCantidad())));
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
    public void liberar(UUID id) {
        Reserva liberada = transaccion.execute(s -> {
            Reserva reserva = reservaRepository.findById(id)
                    .orElseThrow(() -> CodigoError.noEncontrado("La reserva " + id + " no existe"));
            return liberarPendienteOFallarSiConfirmada(reserva) != null ? reserva : null;
        });
        registrarLiberada(liberada, MotivoLiberacion.COMPENSACION);
        avisarSiCambio(liberada == null ? null : liberada.getEventoId());
    }

    /**
     * PENDIENTE pasa a LIBERADA y devuelve el evento afectado. Ya LIBERADA o VENCIDA devuelve null (sin cambios).
     * CONFIRMADA lanza 409 RESERVA_CONFIRMADA.
     */
    private Long liberarPendienteOFallarSiConfirmada(Reserva reserva) {
        if (liberarSiPendiente(reserva.getId())) {
            return reserva.getEventoId();
        }
        if (estadoActual(reserva.getId()) == EstadoReserva.CONFIRMADA) {
            throw CodigoError.reservaConfirmada();
        }
        return null;
    }

    /**
     * Devolucion aprobada sobre una reserva CONFIRMADA. Numerados: cada asiento pasa a LIBRE, su item queda
     * con cantidad 0 y se descuenta de la reserva; un asiento ya devuelto se ignora y uno ajeno responde 409.
     * No numerados: se suma al cupo y se descuenta de la reserva sin superar lo comprado.
     */
    public void devolver(UUID id, DevolucionRequest request) {
        Devolucion devolucion = transaccion.execute(s -> devolverEnTransaccion(id, request));
        if (devolucion != null) {
            Reserva r = devolucion.reserva();
            registro.registrar(Operacion.RESERVA_DEVUELTA, datos(
                    "reservaId", id,
                    "eventoId", r.getEventoId(),
                    "sectorId", r.getSectorId(),
                    "clienteId", r.getClienteId(),
                    "cantidad", devolucion.unidades(),
                    "asientos", asientos(devolucion.asientos())));
        }
        avisarSiCambio(devolucion == null ? null : devolucion.reserva().getEventoId());
    }

    /** Lugares que una devolucion libero de verdad (sin contar los ya devueltos antes). */
    private record Devolucion(Reserva reserva, int unidades, List<AsientoRef> asientos) {
    }

    /** Devuelve lo que se libero, o null si fue una repeticion sin cambios. */
    private Devolucion devolverEnTransaccion(UUID id, DevolucionRequest request) {
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
        ReglasReserva.validarTipoDeSector(tipo, conItems);

        if (conItems) {
            List<AsientoRef> devueltos = devolverAsientos(reserva, request.items());
            return devueltos.isEmpty() ? null : new Devolucion(reserva, devueltos.size(), devueltos);
        }
        devolverCupo(reserva, request.cantidad());
        return new Devolucion(reserva, request.cantidad(), List.of());
    }

    /** Devuelve los asientos que se liberaron en esta llamada (vacia si todos ya estaban devueltos). */
    private List<AsientoRef> devolverAsientos(Reserva reserva, List<AsientoRef> items) {
        ReglasReserva.validarSinRepetidos(items, "items");
        UUID id = reserva.getId();
        List<AsientoRef> ordenados = ReglasReserva.ordenados(items);
        List<String> ajenos = new ArrayList<>();
        List<AsientoRef> liberados = new ArrayList<>();
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
                liberados.add(a);
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
        return liberados;
    }

    private void devolverCupo(Reserva reserva, int cantidad) {
        ReglasReserva.validarCantidad(cantidad);
        UUID id = reserva.getId();
        if (reservaRepository.descontarCantidad(id, cantidad) == 0
                || reservaItemRepository.descontarCupoDevuelto(id, cantidad) == 0) {
            throw CodigoError.estadoInvalido("La devolucion supera lo comprado en la reserva");
        }
        if (inventarioSectorRepository.sumarCupo(reserva.getEventoId(), reserva.getSectorId(), cantidad) == 0) {
            throw CodigoError.estadoInvalido("La devolucion superaria la capacidad del sector");
        }
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
        // Una linea por reserva, escrita despues del commit del lote.
        for (Reserva r : reservaRepository.findAllById(ids)) {
            registro.registrar(Operacion.RESERVA_VENCIDA, datos(
                    "reservaId", r.getId(),
                    "eventoId", r.getEventoId(),
                    "sectorId", r.getSectorId(),
                    "clienteId", r.getClienteId(),
                    "cantidad", r.getCantidad()));
        }
        return ids.size() == loteVencimiento;
    }

    // ------------------------------------------------------------------ auxiliares

    /** UPDATE condicional PENDIENTE -> LIBERADA; solo si aplico se devuelven los lugares. */
    private boolean liberarSiPendiente(UUID id) {
        List<UUID> ids = List.of(id);
        if (reservaRepository.liberar(ids) == 0) {
            return false;
        }
        asientoRepository.liberarDeReservas(ids);
        inventarioSectorRepository.devolverCupoDeReservas(ids);
        return true;
    }

    /** Registra una liberacion que efectivamente cambio la reserva (null = repeticion sin cambios). */
    private void registrarLiberada(Reserva reserva, MotivoLiberacion motivo) {
        if (reserva == null) {
            return;
        }
        registro.registrar(Operacion.RESERVA_LIBERADA, datos(
                "reservaId", reserva.getId(),
                "eventoId", reserva.getEventoId(),
                "sectorId", reserva.getSectorId(),
                "clienteId", reserva.getClienteId(),
                "cantidad", reserva.getCantidad(),
                "motivo", motivo));
    }

    /** Asientos en el formato del contrato (F3-N10,F3-N11), o null en sectores no numerados. */
    private static String asientos(List<AsientoRef> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join(",", items.stream().map(a -> CodigoError.asiento(a.fila(), a.numero())).toList());
    }

    /** Se llama fuera de la transaccion de la operacion, ya sin conexion retenida. */
    private void avisarSiCambio(Long eventoAfectado) {
        if (eventoAfectado != null) {
            avisoAgotadoService.evaluarDespuesDelCommit(eventoAfectado);
        }
    }

    /** Estado leido de la base despues de un UPDATE condicional que no aplico. */
    private EstadoReserva estadoActual(UUID id) {
        return reservaRepository.estadoActual(id)
                .map(EstadoReserva::valueOf)
                .orElseThrow(() -> CodigoError.noEncontrado("La reserva " + id + " no existe"));
    }

    private static ReservaResponse respuesta(Reserva reserva, List<AsientoRef> items) {
        return respuesta(reserva, reserva.getEstado(), items);
    }

    private static ReservaResponse respuesta(Reserva reserva, EstadoReserva estado, List<AsientoRef> items) {
        return new ReservaResponse(reserva.getId(), reserva.getEventoId(), reserva.getSectorId(),
                reserva.getClienteId(), estado, reserva.getCantidad(), items, reserva.getExpiraEn());
    }
}
