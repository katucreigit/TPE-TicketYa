package com.ticketya.reservas.exception;

import org.springframework.http.HttpStatus;

/** 502: fallo otro microservicio o la pasarela de pagos. */
public class BadGatewayException extends ApiException {

    public BadGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, "SERVICIO_NO_DISPONIBLE", message);
    }

    public BadGatewayException(String code, String message) {
        super(HttpStatus.BAD_GATEWAY, code, message);
    }
}
