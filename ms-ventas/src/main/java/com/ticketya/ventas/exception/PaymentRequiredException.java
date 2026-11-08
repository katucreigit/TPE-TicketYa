package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

/** 402: la pasarela rechazo el pago. */
public class PaymentRequiredException extends ApiException {

    public PaymentRequiredException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, "PAGO_RECHAZADO", message);
    }
}
