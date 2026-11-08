package com.ticketya.eventos.exception;

import org.springframework.http.HttpStatus;

/** 404. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", message);
    }

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
