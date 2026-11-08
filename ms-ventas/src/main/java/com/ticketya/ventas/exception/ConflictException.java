package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 409: la operacion no es valida para el estado actual o hay un conflicto de concurrencia. */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String code, String message, List<String> details) {
        super(HttpStatus.CONFLICT, code, message, details);
    }

    public static ConflictException estadoInvalido(String message) {
        return new ConflictException("ESTADO_INVALIDO", message);
    }
}
