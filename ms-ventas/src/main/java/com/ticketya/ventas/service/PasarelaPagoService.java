package com.ticketya.ventas.service;

import com.ticketya.ventas.exception.BadRequestException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoPago;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pasarela de pago simulada (spec pago-simulado y resiliencia-pagos). El resultado se decide por el token:
 * {@code tok_ok} aprueba, {@code tok_rechazado} rechaza, {@code tok_timeout} cobra pero no responde a tiempo,
 * {@code tok_timeout_rechazado} registra rechazo con timeout inicial y {@code tok_timeout_no_concretado}
 * simula timeout sin registrar el cobro. Cada cobro queda vinculado a una clave de cobro idempotente:
 * si se repite o se consulta su estado, no genera un segundo debito.
 */
@Service
public class PasarelaPagoService {

    public static final String TOKEN_OK = "tok_ok";
    public static final String TOKEN_RECHAZADO = "tok_rechazado";
    public static final String TOKEN_TIMEOUT = "tok_timeout";
    public static final String TOKEN_TIMEOUT_RECHAZADO = "tok_timeout_rechazado";
    public static final String TOKEN_TIMEOUT_NO_CONCRETADO = "tok_timeout_no_concretado";
    private static final int DEFAULT_CONSULTAS_INCIERTAS_TIMEOUT = 2;

    /** Resultado de un cobro: {@code APROBADO} con id de transaccion, o {@code RECHAZADO} sin transaccion. */
    public record ResultadoCobro(EstadoPago estado, String transaccionId) {
        public boolean aprobado() {
            return estado == EstadoPago.APROBADO;
        }
    }

    /** Estado devuelto al consultar un cobro por clave de idempotencia. */
    public enum EstadoConsulta {
        APROBADO, RECHAZADO, INCIERTO, NO_ENCONTRADO
    }

    /** Resultado de la consulta de estado de un cobro en la pasarela. */
    public record ResultadoConsultaCobro(EstadoConsulta estado, String transaccionId) {
        public boolean esDefinitivo() {
            return estado == EstadoConsulta.APROBADO || estado == EstadoConsulta.RECHAZADO;
        }

        public boolean aprobado() {
            return estado == EstadoConsulta.APROBADO;
        }

        public boolean rechazado() {
            return estado == EstadoConsulta.RECHAZADO;
        }

        public boolean incierto() {
            return estado == EstadoConsulta.INCIERTO;
        }

        public boolean noEncontrado() {
            return estado == EstadoConsulta.NO_ENCONTRADO;
        }
    }

    /** Resultado de un reembolso. */
    public record ResultadoReembolso(String transaccionId, EstadoPago estado) {
    }

    private static final class SimulacionCobro {
        int timeoutsEnCobroRestantes;
        int consultasInciertasRestantes;
        final boolean timeoutEnConsulta;
        final boolean registrarAlTimeout;
        final EstadoPago resultadoFinal;

        SimulacionCobro(int timeoutsEnCobroRestantes,
                        int consultasInciertasRestantes,
                        boolean timeoutEnConsulta,
                        boolean registrarAlTimeout,
                        EstadoPago resultadoFinal) {
            this.timeoutsEnCobroRestantes = Math.max(0, timeoutsEnCobroRestantes);
            this.consultasInciertasRestantes = Math.max(0, consultasInciertasRestantes);
            this.timeoutEnConsulta = timeoutEnConsulta;
            this.registrarAlTimeout = registrarAlTimeout;
            this.resultadoFinal = resultadoFinal;
        }
    }

    private final int consultasInciertasTimeout;
    private final Map<String, ResultadoCobro> cobrosPorClave = new HashMap<>();
    private final Map<String, EstadoPago> transacciones = new HashMap<>();
    private final Map<String, ResultadoReembolso> reintegrosPorClave = new HashMap<>();
    private final Map<String, SimulacionCobro> simulacionesPorClave = new HashMap<>();

    public PasarelaPagoService() {
        this(DEFAULT_CONSULTAS_INCIERTAS_TIMEOUT);
    }

    @Autowired
    public PasarelaPagoService(
            @Value("${ticketya.pagos.pasarela.consultas-inciertas-timeout:2}") int consultasInciertasTimeout) {
        this.consultasInciertasTimeout = Math.max(0, consultasInciertasTimeout);
    }

    /**
     * Procesa un cobro de forma idempotente por {@code claveCobro}.
     *
     * @throws BadRequestException     (400) token no soportado, monto no positivo o clave de cobro ausente
     * @throws GatewayTimeoutException (504) si el token o la simulacion provocan timeout
     */
    public synchronized ResultadoCobro cobrar(String claveCobro, String token, BigDecimal monto) {
        validarClaveCobro(claveCobro);

        ResultadoCobro previo = cobrosPorClave.get(claveCobro);
        if (previo != null) {
            return previo;
        }
        if (token == null || !esTokenSoportado(token)) {
            throw new BadRequestException("TOKEN_NO_SOPORTADO", "Token de pago no soportado: " + token);
        }
        if (monto == null || monto.signum() <= 0) {
            throw new BadRequestException("El monto debe ser positivo");
        }

        SimulacionCobro simulacion = simulacionesPorClave.get(claveCobro);
        if (simulacion != null && simulacion.timeoutsEnCobroRestantes > 0) {
            simulacion.timeoutsEnCobroRestantes--;
            if (simulacion.registrarAlTimeout && simulacion.resultadoFinal != null) {
                registrarResultadoSimulado(claveCobro, simulacion.resultadoFinal);
            }
            throw new GatewayTimeoutException("La pasarela de pagos no respondio a tiempo");
        }

        if (TOKEN_RECHAZADO.equals(token)) {
            return registrarRechazo(claveCobro);
        }

        if (TOKEN_TIMEOUT.equals(token)) {
            registrarAprobacion(claveCobro);
            simulacionesPorClave.put(claveCobro, new SimulacionCobro(
                    0, consultasInciertasTimeout, false, true, EstadoPago.APROBADO));
            throw new GatewayTimeoutException("La pasarela de pagos no respondio a tiempo");
        }

        if (TOKEN_TIMEOUT_RECHAZADO.equals(token)) {
            registrarRechazo(claveCobro);
            simulacionesPorClave.put(claveCobro, new SimulacionCobro(
                    0, consultasInciertasTimeout, false, true, EstadoPago.RECHAZADO));
            throw new GatewayTimeoutException("La pasarela de pagos no respondio a tiempo");
        }

        if (TOKEN_TIMEOUT_NO_CONCRETADO.equals(token)) {
            simulacionesPorClave.put(claveCobro, new SimulacionCobro(
                    0, consultasInciertasTimeout, false, false, EstadoPago.FALLIDO));
            throw new GatewayTimeoutException("La pasarela de pagos no respondio a tiempo");
        }

        return registrarAprobacion(claveCobro);
    }

    /**
     * Consulta el estado actual de un cobro por su clave de idempotencia sin generar nuevos debitos.
     *
     * @throws BadRequestException     (400) si {@code claveCobro} es nula o vacia
     * @throws GatewayTimeoutException (504) si la simulacion configura timeout en consulta
     */
    public synchronized ResultadoConsultaCobro consultarEstado(String claveCobro) {
        validarClaveCobro(claveCobro);

        SimulacionCobro simulacion = simulacionesPorClave.get(claveCobro);
        if (simulacion != null && simulacion.consultasInciertasRestantes > 0) {
            simulacion.consultasInciertasRestantes--;
            if (simulacion.timeoutEnConsulta) {
                throw new GatewayTimeoutException("La pasarela de pagos no respondio al consultar estado");
            }
            return new ResultadoConsultaCobro(EstadoConsulta.INCIERTO, null);
        }
        if (simulacion != null && simulacion.timeoutsEnCobroRestantes == 0) {
            simulacionesPorClave.remove(claveCobro);
        }

        ResultadoCobro registrado = cobrosPorClave.get(claveCobro);
        if (registrado == null) {
            return new ResultadoConsultaCobro(EstadoConsulta.NO_ENCONTRADO, null);
        }
        if (registrado.aprobado()) {
            return new ResultadoConsultaCobro(EstadoConsulta.APROBADO, registrado.transaccionId());
        }
        return new ResultadoConsultaCobro(EstadoConsulta.RECHAZADO, null);
    }

    /**
     * Programa una simulacion determinista en la que los primeros {@code timeoutsEnCobro} intentos de
     * {@link #cobrar} fallan con timeout sin registrar el cobro en pasarela, y {@code consultasInciertas}
     * consultas iniciales responden {@code INCIERTO} antes de informar {@code NO_ENCONTRADO}.
     */
    public synchronized void programarSimulacionTimeoutNoRegistrado(String claveCobro,
                                                                      int timeoutsEnCobro,
                                                                      int consultasInciertas) {
        validarClaveCobro(claveCobro);
        simulacionesPorClave.put(claveCobro, new SimulacionCobro(
                timeoutsEnCobro, consultasInciertas, false, false, EstadoPago.FALLIDO));
    }

    /**
     * Programa una simulacion determinista de incertidumbre: {@code cobrar} falla la primera vez con
     * timeout (registrando o no el cobro segun {@code resultadoFinal}) y luego {@code consultasInciertas}
     * consultas devuelven {@code INCIERTO} (o lanzan {@link GatewayTimeoutException} si {@code timeoutEnConsulta})
     * antes de resolver al {@code resultadoFinal} ({@code APROBADO}, {@code RECHAZADO} o {@code FALLIDO}).
     */
    public synchronized void programarSimulacionIncertidumbre(String claveCobro,
                                                              int consultasInciertas,
                                                              boolean timeoutEnConsulta,
                                                              EstadoPago resultadoFinal) {
        validarClaveCobro(claveCobro);
        boolean registrar = resultadoFinal == EstadoPago.APROBADO || resultadoFinal == EstadoPago.RECHAZADO;
        simulacionesPorClave.put(claveCobro, new SimulacionCobro(
                1, consultasInciertas, timeoutEnConsulta, registrar, resultadoFinal));
    }

    /**
     * Resuelve de forma inmediata cualquier incertidumbre pendiente para {@code claveCobro} con el resultado
     * indicado ({@code APROBADO}, {@code RECHAZADO} o {@code FALLIDO} para simular no concretado).
     */
    public synchronized void resolverIncertidumbre(String claveCobro, EstadoPago resultadoFinal) {
        validarClaveCobro(claveCobro);
        simulacionesPorClave.remove(claveCobro);
        if (resultadoFinal == EstadoPago.APROBADO || resultadoFinal == EstadoPago.RECHAZADO) {
            registrarResultadoSimulado(claveCobro, resultadoFinal);
        } else {
            cobrosPorClave.remove(claveCobro);
        }
    }

    /** Limpia todas las simulaciones deterministas activas. */
    public synchronized void limpiarSimulaciones() {
        simulacionesPorClave.clear();
    }

    /**
     * Reembolsa una transaccion aprobada. Es idempotente: repetir el reembolso devuelve el mismo resultado
     * sin reintegrar dos veces.
     *
     * @throws NotFoundException (404) si la transaccion no existe
     */
    public synchronized ResultadoReembolso reembolsar(String transaccionId) {
        EstadoPago estado = transacciones.get(transaccionId);
        if (estado == null) {
            throw new NotFoundException("TRANSACCION_NO_ENCONTRADA", "No existe la transaccion " + transaccionId);
        }
        transacciones.put(transaccionId, EstadoPago.REEMBOLSADO);
        return new ResultadoReembolso(transaccionId, EstadoPago.REEMBOLSADO);
    }

    /**
     * Reintegra {@code monto} de un cobro aprobado (devolucion de una entrada). Es idempotente por
     * {@code claveReintegro}: repetirla devuelve el mismo resultado sin reintegrar dos veces. No exige que la
     * transaccion original siga en memoria, porque el mock pierde su estado al reiniciar ms-ventas.
     *
     * @throws BadRequestException (400) clave o transaccion ausente, o monto no positivo
     */
    public synchronized ResultadoReembolso reintegrar(String claveReintegro, String transaccionId, BigDecimal monto) {
        if (claveReintegro == null || claveReintegro.isBlank()) {
            throw new BadRequestException("La clave de reintegro es obligatoria");
        }
        ResultadoReembolso previo = reintegrosPorClave.get(claveReintegro);
        if (previo != null) {
            return previo;
        }
        if (transaccionId == null || transaccionId.isBlank()) {
            throw new BadRequestException("La transaccion a reintegrar es obligatoria");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new BadRequestException("El monto debe ser positivo");
        }
        ResultadoReembolso resultado = new ResultadoReembolso("rf-" + UUID.randomUUID(), EstadoPago.REEMBOLSADO);
        reintegrosPorClave.put(claveReintegro, resultado);
        return resultado;
    }

    private ResultadoCobro registrarAprobacion(String claveCobro) {
        ResultadoCobro existente = cobrosPorClave.get(claveCobro);
        if (existente != null && existente.aprobado()) {
            return existente;
        }
        String transaccionId = "tx-" + UUID.randomUUID();
        ResultadoCobro aprobado = new ResultadoCobro(EstadoPago.APROBADO, transaccionId);
        cobrosPorClave.put(claveCobro, aprobado);
        transacciones.put(transaccionId, EstadoPago.APROBADO);
        return aprobado;
    }

    private ResultadoCobro registrarRechazo(String claveCobro) {
        ResultadoCobro rechazado = new ResultadoCobro(EstadoPago.RECHAZADO, null);
        cobrosPorClave.put(claveCobro, rechazado);
        return rechazado;
    }

    private void registrarResultadoSimulado(String claveCobro, EstadoPago resultadoFinal) {
        if (resultadoFinal == EstadoPago.APROBADO) {
            registrarAprobacion(claveCobro);
        } else if (resultadoFinal == EstadoPago.RECHAZADO) {
            registrarRechazo(claveCobro);
        }
    }

    private static void validarClaveCobro(String claveCobro) {
        if (claveCobro == null || claveCobro.isBlank()) {
            throw new BadRequestException("La clave de cobro es obligatoria");
        }
    }

    private static boolean esTokenSoportado(String token) {
        return TOKEN_OK.equals(token)
                || TOKEN_RECHAZADO.equals(token)
                || TOKEN_TIMEOUT.equals(token)
                || TOKEN_TIMEOUT_RECHAZADO.equals(token)
                || TOKEN_TIMEOUT_NO_CONCRETADO.equals(token);
    }
}
