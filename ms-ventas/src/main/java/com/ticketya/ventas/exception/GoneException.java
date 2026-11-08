package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

/** 410: la reserva vencio (no se cobro, o se reembolso el pago). */
public class GoneException extends ApiException {

    public GoneException(String message) {
        super(HttpStatus.GONE, "RESERVA_VENCIDA", message);
    }
}
