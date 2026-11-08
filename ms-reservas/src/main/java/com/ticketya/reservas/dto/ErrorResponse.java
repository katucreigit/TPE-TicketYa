package com.ticketya.reservas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Formato de error estandar (schema {@code Error} de openapi.yaml), igual en todos los servicios.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String code, String message,
                                   String path, List<String> details) {
        return new ErrorResponse(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                status, error, code, message, path, details == null ? List.of() : List.copyOf(details));
    }
}
