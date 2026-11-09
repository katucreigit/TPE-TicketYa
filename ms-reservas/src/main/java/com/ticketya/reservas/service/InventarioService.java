package com.ticketya.reservas.service;

import com.ticketya.reservas.dto.AsientoEstadoResponse;
import com.ticketya.reservas.dto.DisponibilidadResponse;
import com.ticketya.reservas.dto.InventarioRequest;
import com.ticketya.reservas.exception.CodigoError;
import com.ticketya.reservas.model.InventarioEvento;
import com.ticketya.reservas.model.InventarioSector;
import com.ticketya.reservas.model.enums.EstadoAsiento;
import com.ticketya.reservas.model.enums.TipoSector;
import com.ticketya.reservas.repository.AsientoRepository;
import com.ticketya.reservas.repository.InventarioEventoRepository;
import com.ticketya.reservas.repository.InventarioSectorRepository;
import com.ticketya.reservas.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Inventario de lugares de cada evento: inicializacion, disponibilidad, mapa de asientos, cierre y reapertura.
 * Toda la concurrencia se resuelve en la base con UPDATE condicionales y bloqueos de fila.
 */
@Service
public class InventarioService {

    private final InventarioEventoRepository inventarioEventoRepository;
    private final InventarioSectorRepository inventarioSectorRepository;
    private final AsientoRepository asientoRepository;
    private final ReservaRepository reservaRepository;
    private final AvisoAgotadoService avisoAgotadoService;
    private final int maxPorClienteDefault;
    private final TransactionTemplate transaccion;

    public InventarioService(InventarioEventoRepository inventarioEventoRepository,
                             InventarioSectorRepository inventarioSectorRepository,
                             AsientoRepository asientoRepository,
                             ReservaRepository reservaRepository,
                             AvisoAgotadoService avisoAgotadoService,
                             PlatformTransactionManager transactionManager,
                             @Value("${ticketya.reserva.max-por-cliente-default:4}") int maxPorClienteDefault) {
        this.transaccion = new TransactionTemplate(transactionManager);
        this.inventarioEventoRepository = inventarioEventoRepository;
        this.inventarioSectorRepository = inventarioSectorRepository;
        this.asientoRepository = asientoRepository;
        this.reservaRepository = reservaRepository;
        this.avisoAgotadoService = avisoAgotadoService;
        this.maxPorClienteDefault = maxPorClienteDefault;
    }

    /**
     * Crea el inventario en una sola transaccion: si algo falla no queda nada creado.
     * Si el inventario ya existe responde 409 sin tocar nada.
     */
    @Transactional
    public void crearInventario(InventarioRequest request) {
        validar(request);
        Long eventoId = request.eventoId();
        int maxPorCliente = request.maxPorCliente() != null ? request.maxPorCliente() : maxPorClienteDefault;

        if (inventarioEventoRepository.insertarSiNoExiste(eventoId, maxPorCliente) == 0) {
            throw CodigoError.estadoInvalido("El inventario del evento " + eventoId + " ya existe");
        }
        InventarioEvento evento = inventarioEventoRepository.getReferenceById(eventoId);

        for (InventarioRequest.Sector datos : request.sectores()) {
            boolean numerado = datos.tipo() == TipoSector.NUMERADO;
            int capacidad = numerado ? datos.filas() * datos.asientosPorFila() : datos.capacidad();

            InventarioSector sector = new InventarioSector();
            sector.setInventarioEvento(evento);
            sector.setSectorId(datos.sectorId());
            sector.setTipo(datos.tipo());
            sector.setCapacidadTotal(capacidad);
            // En numerados los libres se cuentan sobre los asientos; disponibles queda como snapshot de la capacidad.
            sector.setDisponibles(capacidad);
            sector = inventarioSectorRepository.save(sector);

            if (numerado) {
                asientoRepository.insertarAsientosLibres(sector.getId(), eventoId, datos.sectorId(),
                        datos.filas(), datos.asientosPorFila());
            }
        }
    }

    @Transactional(readOnly = true)
    public DisponibilidadResponse disponibilidad(Long eventoId) {
        if (!inventarioEventoRepository.existsById(eventoId)) {
            throw CodigoError.noEncontrado("El evento " + eventoId + " no tiene inventario");
        }
        List<DisponibilidadResponse.Sector> sectores = inventarioSectorRepository.disponibilidadPorEvento(eventoId)
                .stream()
                .map(v -> new DisponibilidadResponse.Sector(v.getSectorId(), TipoSector.valueOf(v.getTipo()),
                        v.getCapacidad(), v.getDisponibles()))
                .toList();
        return new DisponibilidadResponse(eventoId, sectores);
    }

    @Transactional(readOnly = true)
    public List<AsientoEstadoResponse> asientos(Long eventoId, Long sectorId, EstadoAsiento estado) {
        InventarioSector sector = inventarioSectorRepository
                .findByInventarioEventoEventoIdAndSectorId(eventoId, sectorId)
                .orElseThrow(() -> CodigoError.noEncontrado(
                        "El sector " + sectorId + " no existe en el inventario del evento " + eventoId));
        if (sector.getTipo() != TipoSector.NUMERADO) {
            throw CodigoError.validacion("El sector " + sectorId + " no es numerado", List.of());
        }
        var asientos = estado == null
                ? asientoRepository.findByEventoIdAndSectorIdOrderByFilaAscNumeroAsc(eventoId, sectorId)
                : asientoRepository.findByEventoIdAndSectorIdAndEstadoOrderByFilaAscNumeroAsc(eventoId, sectorId, estado);
        return asientos.stream()
                .map(a -> new AsientoEstadoResponse(a.getFila(), a.getNumero(), a.getEstado()))
                .toList();
    }

    /**
     * Cierra el inventario (suspension o cancelacion): no admite nuevas reservas, las PENDIENTE pasan a
     * LIBERADA y sus lugares vuelven. Repetible: un segundo cierre no encuentra pendientes y no cambia nada.
     * No se avisa agotado a ms-eventos mientras el evento esta cerrado.
     */
    @Transactional
    public void cerrar(Long eventoId) {
        // El UPDATE toma el lock de la fila del evento antes de bloquear sus reservas pendientes.
        if (inventarioEventoRepository.cerrar(eventoId) == 0) {
            throw CodigoError.noEncontrado("El evento " + eventoId + " no tiene inventario");
        }
        List<UUID> pendientes = reservaRepository.bloquearPendientesDeEvento(eventoId);
        if (pendientes.isEmpty()) {
            return;
        }
        reservaRepository.liberar(pendientes);
        asientoRepository.liberarDeReservas(pendientes);
        inventarioSectorRepository.devolverCupoDeReservas(pendientes);
    }

    /**
     * Reabre el inventario (reanudacion) y, tras el commit, avisa a ms-eventos si el evento esta agotado.
     * El aviso se evalua al salir de la transaccion para no retener su conexion mientras abre otra
     * (mismo patron que ReservaService). Con una transaccion externa, evaluarDespuesDelCommit espera a su commit.
     */
    public void abrir(Long eventoId) {
        transaccion.executeWithoutResult(s -> {
            if (inventarioEventoRepository.abrir(eventoId) == 0) {
                throw CodigoError.noEncontrado("El evento " + eventoId + " no tiene inventario");
            }
        });
        avisoAgotadoService.evaluarDespuesDelCommit(eventoId);
    }

    private void validar(InventarioRequest request) {
        List<String> errores = new ArrayList<>();
        if (request.maxPorCliente() != null && request.maxPorCliente() < 1) {
            errores.add("maxPorCliente debe ser mayor o igual a 1");
        }
        Set<Long> vistos = new HashSet<>();
        for (int i = 0; i < request.sectores().size(); i++) {
            InventarioRequest.Sector s = request.sectores().get(i);
            String campo = "sectores[" + i + "]";
            if (!vistos.add(s.sectorId())) {
                errores.add(campo + ".sectorId " + s.sectorId() + " esta repetido");
            }
            if (s.tipo() == TipoSector.NUMERADO) {
                if (!positivo(s.filas())) {
                    errores.add(campo + ".filas debe ser mayor a 0 en un sector numerado");
                }
                if (!positivo(s.asientosPorFila())) {
                    errores.add(campo + ".asientosPorFila debe ser mayor a 0 en un sector numerado");
                }
                if (positivo(s.filas()) && positivo(s.asientosPorFila())
                        && (long) s.filas() * s.asientosPorFila() > Integer.MAX_VALUE) {
                    errores.add(campo + " tiene demasiados asientos");
                }
            } else if (!positivo(s.capacidad())) {
                errores.add(campo + ".capacidad debe ser mayor a 0 en un sector no numerado");
            }
        }
        if (!errores.isEmpty()) {
            throw CodigoError.validacion("Datos invalidos", errores);
        }
    }

    private static boolean positivo(Integer valor) {
        return valor != null && valor > 0;
    }
}
