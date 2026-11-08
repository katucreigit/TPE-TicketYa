package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

/** 403. */
public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tiene permiso para esta operacion");
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", message);
    }
}
