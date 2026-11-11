package com.ticketya.ventas.service;

import com.ticketya.ventas.exception.BadRequestException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoPago;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pasarela de pago simulada (spec pago-simulado). El resultado se decide por el token:
 * {@code tok_ok} aprueba, {@code tok_rechazado} rechaza y {@code tok_timeout} cobra pero no responde a tiempo
 * (504 la primera vez). Cada cobro queda vinculado a una clave de cobro: si se repite, se devuelve el resultado
 * almacenado sin cobrar de nuevo. El estado vive en memoria porque es un mock.
 */
@Service
public class PasarelaPagoService {

    public static final String TOKEN_OK = "tok_ok";
    public static final String TOKEN_RECHAZADO = "tok_rechazado";
    public static final String TOKEN_TIMEOUT = "tok_timeout";

    /** Resultado de un cobro: {@code APROBADO} con id de transaccion, o {@code RECHAZADO} sin transaccion. */
    public record ResultadoCobro(EstadoPago estado, String transaccionId) {
        public boolean aprobado() {
            return estado == EstadoPago.APROBADO;
        }
    }

    /** Resultado de un reembolso. */
    public record ResultadoReembolso(String transaccionId, EstadoPago estado) {
    }

    private final Map<String, ResultadoCobro> cobrosPorClave = new HashMap<>();
    private final Map<String, EstadoPago> transacciones = new HashMap<>();
    private final Map<String, ResultadoReembolso> reintegrosPorClave = new HashMap<>();

    /**
     * Procesa un cobro.
     *
     * @throws BadRequestException     (400) token no soportado, monto no positivo o clave de cobro ausente
     * @throws GatewayTimeoutException (504) con {@code tok_timeout} la primera vez: el cobro queda registrado
     *                                 como aprobado y un reintento con la misma clave lo devuelve sin duplicarlo
     */
    public synchronized ResultadoCobro cobrar(String claveCobro, String token, BigDecimal monto) {
        if (claveCobro == null || claveCobro.isBlank()) {
            throw new BadRequestException("La clave de cobro es obligatoria");
        }
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

        if (TOKEN_RECHAZADO.equals(token)) {
            ResultadoCobro rechazado = new ResultadoCobro(EstadoPago.RECHAZADO, null);
            cobrosPorClave.put(claveCobro, rechazado);
            return rechazado;
        }

        String transaccionId = "tx-" + UUID.randomUUID();
        ResultadoCobro aprobado = new ResultadoCobro(EstadoPago.APROBADO, transaccionId);
        cobrosPorClave.put(claveCobro, aprobado);
        transacciones.put(transaccionId, EstadoPago.APROBADO);

        if (TOKEN_TIMEOUT.equals(token)) {
            throw new GatewayTimeoutException("La pasarela de pagos no respondio a tiempo");
        }
        return aprobado;
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

    private static boolean esTokenSoportado(String token) {
        return TOKEN_OK.equals(token) || TOKEN_RECHAZADO.equals(token) || TOKEN_TIMEOUT.equals(token);
    }
}

