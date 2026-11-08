package com.ticketya.reservas.exception;

import org.springframework.http.HttpStatus;

/** 410: la reserva vencio. */
public class GoneException extends ApiException {

    public GoneException(String message) {
        super(HttpStatus.GONE, "RESERVA_VENCIDA", message);
    }
}
