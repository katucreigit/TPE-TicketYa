package com.ticketya.reservas.exception;

import java.util.List;

/**
 * Codigos de error de ms-reservas tal como figuran en docs/openapi.yaml, con fabricas que construyen
 * la {@link ApiException} con el status del contrato. No agregar codigos que no esten en el contrato.
 */
public final class CodigoError {

    public static final String VALIDACION = "VALIDACION";                       // 400
    public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";             // 403
    public static final String NO_ENCONTRADO = "NO_ENCONTRADO";                 // 404
    public static final String ESTADO_INVALIDO = "ESTADO_INVALIDO";             // 409
    public static final String ASIENTO_NO_DISPONIBLE = "ASIENTO_NO_DISPONIBLE"; // 409
    public static final String CUPO_INSUFICIENTE = "CUPO_INSUFICIENTE";         // 409
    public static final String EVENTO_NO_DISPONIBLE = "EVENTO_NO_DISPONIBLE";   // 409
    public static final String RESERVA_CONFIRMADA = "RESERVA_CONFIRMADA";       // 409
    public static final String RESERVA_VENCIDA = "RESERVA_VENCIDA";             // 410
    public static final String LIMITE_POR_CLIENTE = "LIMITE_POR_CLIENTE";       // 422

    private CodigoError() {
    }

    public static BadRequestException validacion(String message, List<String> details) {
        return new BadRequestException(VALIDACION, message, details);
    }

    public static ForbiddenException accesoDenegado() {
        return new ForbiddenException();
    }

    public static NotFoundException noEncontrado(String message) {
        return new NotFoundException(NO_ENCONTRADO, message);
    }

    public static ConflictException estadoInvalido(String message) {
        return ConflictException.estadoInvalido(message);
    }

    /** details lleva cada asiento en conflicto con el formato del contrato, p. ej. {@code F3-N10}. */
    public static ConflictException asientoNoDisponible(List<String> asientos) {
        return new ConflictException(ASIENTO_NO_DISPONIBLE,
                "Los asientos " + String.join(", ", asientos) + " ya no estan disponibles", asientos);
    }

    public static ConflictException cupoInsuficiente() {
        return new ConflictException(CUPO_INSUFICIENTE, "No hay cupo suficiente en el sector");
    }

    public static ConflictException eventoNoDisponible() {
        return new ConflictException(EVENTO_NO_DISPONIBLE, "El evento no admite reservas");
    }

    public static ConflictException reservaConfirmada() {
        return new ConflictException(RESERVA_CONFIRMADA, "Una reserva confirmada no se puede cancelar");
    }

    public static GoneException reservaVencida() {
        return new GoneException("La reserva vencio");
    }

    public static BusinessRuleException limitePorCliente(int maximo, long yaTiene) {
        return new BusinessRuleException(LIMITE_POR_CLIENTE,
                "Maximo " + maximo + " entradas por cliente para este evento (ya tiene " + yaTiene + ")");
    }

    /** Formato del contrato para identificar un asiento en details. */
    public static String asiento(int fila, int numero) {
        return "F" + fila + "-N" + numero;
    }
}
