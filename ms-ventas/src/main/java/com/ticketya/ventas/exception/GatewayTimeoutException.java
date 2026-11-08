package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

/** 504: la pasarela de pagos no respondio a tiempo. */
public class GatewayTimeoutException extends ApiException {

    public GatewayTimeoutException(String message) {
        super(HttpStatus.GATEWAY_TIMEOUT, "PASARELA_TIMEOUT", message);
    }
}
