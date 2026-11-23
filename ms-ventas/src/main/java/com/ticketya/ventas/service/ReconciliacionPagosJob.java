package com.ticketya.ventas.service;

import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.dto.CompraResponse;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.repository.CompraRepository;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoConsultaCobro;
import com.ticketya.ventas.trazabilidad.Correlacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Job periodico de reconciliacion de compras en {@code PAGO_INCIERTO} (TS-03).
 * No usa {@code @Transactional}: todas las consultas a la pasarela y llamadas HTTP a otros
 * microservicios se ejecutan fuera de transacciones de BD y solo las escrituras puntuales
 * se delegan a {@link CompraService} (respaldadas por {@code TransactionTemplate}).
 */
@Service
public class ReconciliacionPagosJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacionPagosJob.class);

    private final CompraRepository compraRepository;
    private final CompraService compraService;
    private final PasarelaPagoService pasarelaPagoService;
    private final ReservasClient reservasClient;
    private final EventosClient eventosClient;

    public ReconciliacionPagosJob(CompraRepository compraRepository,
                                  CompraService compraService,
                                  PasarelaPagoService pasarelaPagoService,
                                  ReservasClient reservasClient,
                                  EventosClient eventosClient) {
        this.compraRepository = compraRepository;
        this.compraService = compraService;
        this.pasarelaPagoService = pasarelaPagoService;
        this.reservasClient = reservasClient;
        this.eventosClient = eventosClient;
    }

    @Scheduled(
            fixedDelayString = "${ticketya.pagos.reconciliacion.intervalo-ms:30000}",
            initialDelayString = "${ticketya.pagos.reconciliacion.intervalo-ms:30000}"
    )
    public void reconciliarPagosInciertos() {
        // Cada corrida tiene su propio correlationId: relaciona las reconciliaciones con las llamadas salientes.
        Correlacion.enProceso(() -> {
            List<Compra> inciertas = compraRepository.findByEstadoOrderByCreadaEnAsc(EstadoCompra.PAGO_INCIERTO);
            for (Compra compra : inciertas) {
                if (!compraService.intentarAdquirirResolucion(compra.getId())) {
                    continue;
                }
                try {
                    reconciliarUna(compra);
                } catch (RuntimeException ex) {
                    log.warn("Error al reconciliar compra {}: {}", compra.getId(), ex.getMessage());
                } finally {
                    compraService.liberarResolucion(compra.getId());
                }
            }
        });
    }

    private void reconciliarUna(Compra compra) {
        String claveCobro = CompraService.claveCobroDe(compra.getId());
        ResultadoConsultaCobro consulta;
        try {
            consulta = pasarelaPagoService.consultarEstado(claveCobro);
        } catch (GatewayTimeoutException ex) {
            log.debug("Consulta de estado en timeout para compra {}, permanece en PAGO_INCIERTO", compra.getId());
            return;
        }

        switch (consulta.estado()) {
            case APROBADO -> reconciliarAprobada(compra, consulta.transaccionId());
            case RECHAZADO -> reconciliarRechazada(compra);
            case NO_ENCONTRADO -> reconciliarNoConcretada(compra);
            case INCIERTO -> {
                // La pasarela aun no define el cobro: se mantiene PAGO_INCIERTO para la siguiente corrida.
            }
        }
    }

    private void reconciliarAprobada(Compra compra, String transaccionId) {
        ReservaRemota reserva;
        try {
            reserva = reservasClient.obtener(compra.getReservaId(), compra.getClienteId());
        } catch (BadGatewayException ex) {
            log.warn("ms-reservas no disponible al reconciliar compra {}: {}", compra.getId(), ex.getMessage());
            return;
        } catch (NotFoundException ex) {
            reembolsarYMarcarFallida(compra, compra.getTotal(), transaccionId);
            return;
        }

        String estadoReserva = reserva.estado();
        if ("VENCIDA".equals(estadoReserva) || "LIBERADA".equals(estadoReserva)) {
            reembolsarYMarcarFallida(compra, compra.getTotal(), transaccionId);
            return;
        }

        SnapshotCompra snapshot = eventosClient.obtenerSnapshotParaCompra(reserva.eventoId(), reserva.sectorId());
        BigDecimal total = snapshot.precioUnitario().multiply(BigDecimal.valueOf(reserva.cantidad()));

        if (!"CONFIRMADA".equals(estadoReserva)) {
            try {
                reservasClient.confirmar(reserva.reservaId());
            } catch (GoneException | ConflictException ex) {
                reembolsarYMarcarFallida(compra, total, transaccionId);
                return;
            } catch (BadGatewayException ex) {
                log.warn("Falla temporal al confirmar reserva {} para compra {}: {}",
                        reserva.reservaId(), compra.getId(), ex.getMessage());
                return;
            }
        }

        CompraResponse aprobada = compraService.guardarAprobadaConEntradas(
                compra.getId(), null, total, transaccionId, reserva, snapshot);
        compraService.notificarCompraConfirmada(
                compra.getClienteId(), compra.getClienteEmail(), snapshot.eventoId(), aprobada);
    }

    private void reconciliarRechazada(Compra compra) {
        if (!intentarLiberarReserva(compra)) {
            return;
        }
        compraService.guardarRechazada(compra.getId(), null, compra.getTotal());
    }

    private void reconciliarNoConcretada(Compra compra) {
        if (!intentarLiberarReserva(compra)) {
            return;
        }
        compraService.guardarFallidaNoConcretado(compra.getId(), compra.getTotal());
    }

    private boolean intentarLiberarReserva(Compra compra) {
        try {
            reservasClient.liberar(compra.getReservaId());
            return true;
        } catch (NotFoundException ex) {
            return true;
        } catch (BadGatewayException ex) {
            log.warn("Falla temporal al liberar reserva {} para compra {}: {}",
                    compra.getReservaId(), compra.getId(), ex.getMessage());
            return false;
        }
    }

    private void reembolsarYMarcarFallida(Compra compra, BigDecimal total, String transaccionId) {
        pasarelaPagoService.reembolsar(transaccionId);
        compraService.guardarFallidaConReembolso(compra.getId(), null, total, transaccionId);
    }
}
