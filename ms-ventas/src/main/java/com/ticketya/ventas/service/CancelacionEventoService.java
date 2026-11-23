package com.ticketya.ventas.service;

import com.ticketya.ventas.client.NotificacionesClient;
import com.ticketya.ventas.client.NotificacionesClient.TipoNotificacion;
import com.ticketya.ventas.dto.CancelacionEventoResponse;
import com.ticketya.ventas.model.Compra;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final NotificacionesClient notificacionesClient;
    private final TransactionTemplate transaccion;

    public CancelacionEventoService(EntradaRepository entradaRepository,
                                    ReintegroRepository reintegroRepository,
                                    PasarelaPagoService pasarelaPagoService,
                                    NotificacionesClient notificacionesClient,
                                    TransactionTemplate transaccion) {
        this.entradaRepository = entradaRepository;
        this.reintegroRepository = reintegroRepository;
        this.pasarelaPagoService = pasarelaPagoService;
        this.notificacionesClient = notificacionesClient;
        this.transaccion = transaccion;
    }

    /** Datos para llamar a la pasarela fuera de la transaccion. */
    private record ReintegroPendiente(UUID reintegroId, UUID entradaId, String transaccionPago, BigDecimal monto) {
    }

    private record ResumenComprador(
            String clienteId,
            String clienteEmail,
            List<String> comprasIds,
            long entradasAfectadas,
            long reintegrosCompletados,
            long reintegrosPendientes,
            BigDecimal montoReintegrado) {
    }

    private record ResultadoCancelacion(CancelacionEventoResponse respuesta, List<ResumenComprador> compradores) {
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

        ResultadoCancelacion resultado = transaccion.execute(status -> {
            long completados = reintegroRepository.countByEntradaEventoIdAndTipoAndEstado(
                    eventoId, TIPO, EstadoReintegro.COMPLETADO);
            long pendientesRestantes = reintegroRepository.countByEntradaEventoIdAndTipoAndEstado(
                    eventoId, TIPO, EstadoReintegro.PENDIENTE);
            CancelacionEventoResponse respuesta = new CancelacionEventoResponse(
                    eventoId, completados + pendientesRestantes, completados, pendientesRestantes);
            List<ResumenComprador> compradores = construirResumenCompradores(
                    reintegroRepository.buscarTodosPorEvento(eventoId, TIPO));
            return new ResultadoCancelacion(respuesta, compradores);
        });

        for (ResumenComprador comprador : resultado.compradores()) {
            notificarEventoCancelado(eventoId, comprador);
        }

        return resultado.respuesta();
    }

    private static List<ResumenComprador> construirResumenCompradores(List<Reintegro> todos) {
        if (todos.isEmpty()) {
            return List.of();
        }
        List<Reintegro> ordenados = todos.stream()
                .sorted(Comparator.comparing((Reintegro r) -> r.getEntrada().getCompra().getClienteId())
                        .thenComparing(r -> r.getEntrada().getCompra().getId())
                        .thenComparing(Reintegro::getId))
                .toList();

        Map<String, AcumuladorComprador> porCliente = new LinkedHashMap<>();
        for (Reintegro reintegro : ordenados) {
            Compra compra = reintegro.getEntrada().getCompra();
            porCliente.computeIfAbsent(compra.getClienteId(), AcumuladorComprador::new)
                    .agregar(compra, reintegro);
        }

        List<ResumenComprador> resumenes = new ArrayList<>(porCliente.size());
        for (AcumuladorComprador acc : porCliente.values()) {
            resumenes.add(acc.aResumen());
        }
        return List.copyOf(resumenes);
    }

    private void notificarEventoCancelado(Long eventoId, ResumenComprador comprador) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("eventoId", eventoId);
        datos.put("comprasIds", comprador.comprasIds());
        datos.put("entradasAfectadas", comprador.entradasAfectadas());
        datos.put("reintegrosCompletados", comprador.reintegrosCompletados());
        datos.put("reintegrosPendientes", comprador.reintegrosPendientes());
        datos.put("montoReintegrado", comprador.montoReintegrado());

        notificacionesClient.enviar(
                TipoNotificacion.EVENTO_CANCELADO,
                comprador.clienteId(),
                comprador.clienteEmail(),
                datos,
                "evento-cancelado-" + eventoId + "-" + comprador.clienteId()
        );
    }

    private static final class AcumuladorComprador {
        private final String clienteId;
        private String clienteEmail;
        private final Set<String> comprasIds = new LinkedHashSet<>();
        private long entradasAfectadas;
        private long completados;
        private long pendientes;
        private BigDecimal montoReintegrado = BigDecimal.ZERO;

        private AcumuladorComprador(String clienteId) {
            this.clienteId = clienteId;
        }

        void agregar(Compra compra, Reintegro reintegro) {
            if (clienteEmail == null && compra.getClienteEmail() != null && !compra.getClienteEmail().isBlank()) {
                clienteEmail = compra.getClienteEmail();
            }
            comprasIds.add(compra.getId().toString());
            entradasAfectadas++;
            if (reintegro.getEstado() == EstadoReintegro.COMPLETADO) {
                completados++;
                montoReintegrado = montoReintegrado.add(reintegro.getMonto());
            } else {
                pendientes++;
            }
        }

        ResumenComprador aResumen() {
            return new ResumenComprador(clienteId, clienteEmail, List.copyOf(comprasIds),
                    entradasAfectadas, completados, pendientes, montoReintegrado);
        }
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
