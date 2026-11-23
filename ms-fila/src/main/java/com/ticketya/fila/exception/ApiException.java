package com.ticketya.fila.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Excepcion de negocio base: lleva el status HTTP y el codigo de error que se devuelve en {@code Error.code}.
 * Codigos de ms-fila (docs/openapi.yaml): VALIDACION (400), ACCESO_DENEGADO (403), FILA_NO_ACTIVA (409) y
 * SERVICIO_NO_DISPONIBLE (503).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiException validacion(String message, List<String> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", message, details);
    }

    public static ApiException accesoDenegado() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tiene permiso para esta operacion");
    }

    public static ApiException filaNoActiva(Long eventoId) {
        return new ApiException(HttpStatus.CONFLICT, "FILA_NO_ACTIVA",
                "El evento " + eventoId + " no tiene la fila virtual activa");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
