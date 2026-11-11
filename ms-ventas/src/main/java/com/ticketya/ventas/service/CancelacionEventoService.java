package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.CancelacionEventoResponse;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import com.ticketya.ventas.repository.EntradaRepository;
import com.ticketya.ventas.repository.ReintegroRepository;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoReembolso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Reintegro masivo de las entradas de un evento cancelado (spec cancelacion-evento-reintegros).
 * <p>
 * No libera lugares: ms-reservas ya cerro el inventario del evento. Cada entrada EMITIDA pasa a
 * REINTEGRO_PENDIENTE con un {@link Reintegro} de tipo CANCELACION_EVENTO (UNIQUE(entrada_id, tipo)), y luego se
 * reintegra de a una en la pasarela con la misma clave que usan las devoluciones ({@link
 * DevolucionService#claveReintegroDe}). Si la pasarela falla, la entrada queda REINTEGRO_PENDIENTE y se sigue con
 * las demas; repetir la cancelacion reintenta solo esos reintegros y nunca devuelve el dinero dos veces.
 */
@Service
public class CancelacionEventoService {

    private static final Logger log = LoggerFactory.getLogger(CancelacionEventoService.class);
    private static final TipoReintegro TIPO = TipoReintegro.CANCELACION_EVENTO;

    private final EntradaRepository entradaRepository;
    private final ReintegroRepository reintegroRepository;
    private final PasarelaPagoService pasarelaPagoService;
    private final TransactionTemplate transaccion;

    public CancelacionEventoService(EntradaRepository entradaRepository,
                                    ReintegroRepository reintegroRepository,
                                    PasarelaPagoService pasarelaPagoService,
                                    TransactionTemplate transaccion) {
        this.entradaRepository = entradaRepository;
        this.reintegroRepository = reintegroRepository;
        this.pasarelaPagoService = pasarelaPagoService;
        this.transaccion = transaccion;
    }

    /** Datos para llamar a la pasarela fuera de la transaccion. */
    private record ReintegroPendiente(UUID reintegroId, UUID entradaId, String transaccionPago, BigDecimal monto) {
    }

    /**
     * Procesa el aviso de cancelacion del evento. Idempotente: se puede llamar cuantas veces haga falta.
     * Un evento sin entradas (o desconocido para ms-ventas) responde con contadores en cero.
     */
    public CancelacionEventoResponse procesar(Long eventoId) {
        transaccion.executeWithoutResult(status -> marcarPendientes(eventoId));

        List<ReintegroPendiente> pendientes = transaccion.execute(status ->
                reintegroRepository.buscarPorEvento(eventoId, TIPO, EstadoReintegro.PENDIENTE).stream()
                        .map(CancelacionEventoService::pendienteDe)
                        .toList());

        for (ReintegroPendiente pendiente : pendientes) {
            reintegrar(pendiente);
        }

        return transaccion.execute(status -> {
            long completados = reintegroRepository.countByEntradaEventoIdAndTipoAndEstado(
                    eventoId, TIPO, EstadoReintegro.COMPLETADO);
            long pendientesRestantes = reintegroRepository.countByEntradaEventoIdAndTipoAndEstado(
                    eventoId, TIPO, EstadoReintegro.PENDIENTE);
            return new CancelacionEventoResponse(eventoId, completados + pendientesRestantes,
                    completados, pendientesRestantes);
        });
    }

    /** Pasa las entradas EMITIDA a REINTEGRO_PENDIENTE y les crea (o reutiliza) su reintegro de cancelacion. */
    private void marcarPendientes(Long eventoId) {
        List<Entrada> emitidas = entradaRepository.bloquearPorEventoYEstado(eventoId, EstadoEntrada.EMITIDA);
        for (Entrada entrada : emitidas) {
            if (reintegroRepository.findByEntradaIdAndTipo(entrada.getId(), TIPO).isEmpty()) {
                Reintegro reintegro = new Reintegro();
                reintegro.setEntrada(entrada);
                reintegro.setTipo(TIPO);
                reintegro.setMonto(entrada.getPrecioUnitario());
                reintegro.setEstado(EstadoReintegro.PENDIENTE);
                reintegroRepository.saveAndFlush(reintegro);
            }
            entrada.setEstado(EstadoEntrada.REINTEGRO_PENDIENTE);
            entradaRepository.saveAndFlush(entrada);
        }
        if (!emitidas.isEmpty()) {
            log.info("Evento {} cancelado: {} entradas pasan a REINTEGRO_PENDIENTE", eventoId, emitidas.size());
        }
    }

    private static ReintegroPendiente pendienteDe(Reintegro reintegro) {
        Entrada entrada = reintegro.getEntrada();
        Pago pago = entrada.getCompra().getPago();
        return new ReintegroPendiente(reintegro.getId(), entrada.getId(),
                pago != null ? pago.getTransaccionPasarela() : null, reintegro.getMonto());
    }

    /**
     * Reintegra una entrada. Una falla (de la pasarela o al registrar el resultado) se registra y deja la entrada
     * REINTEGRO_PENDIENTE; el reintento usa la misma clave, asi que la pasarela no paga dos veces.
     */
    private void reintegrar(ReintegroPendiente pendiente) {
        try {
            ResultadoReembolso resultado = pasarelaPagoService.reintegrar(
                    DevolucionService.claveReintegroDe(pendiente.reintegroId()),
                    pendiente.transaccionPago(), pendiente.monto());
            transaccion.executeWithoutResult(status -> completar(pendiente.reintegroId(), resultado.transaccionId()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo reintegrar la entrada {} (reintegro {}); queda REINTEGRO_PENDIENTE: {}",
                    pendiente.entradaId(), pendiente.reintegroId(), ex.getMessage());
        }
    }

    private void completar(UUID reintegroId, String transaccionReintegro) {
        Reintegro reintegro = reintegroRepository.bloquear(reintegroId).orElseThrow();
        if (reintegro.getEstado() == EstadoReintegro.COMPLETADO) {
            return;
        }
        reintegro.setEstado(EstadoReintegro.COMPLETADO);
        reintegro.setTransaccionPasarela(transaccionReintegro);
        reintegroRepository.saveAndFlush(reintegro);
        Entrada entrada = reintegro.getEntrada();
        entrada.setEstado(EstadoEntrada.REINTEGRADA);
        entradaRepository.saveAndFlush(entrada);
    }
}
