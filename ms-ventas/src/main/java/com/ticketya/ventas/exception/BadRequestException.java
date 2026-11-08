package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 400. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDACION", message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String code, String message, List<String> details) {
        super(HttpStatus.BAD_REQUEST, code, message, details);
    }
}
