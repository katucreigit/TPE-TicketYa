package com.ticketya.ventas.exception;

import org.springframework.http.HttpStatus;

/** 422: la solicitud es valida pero viola una regla de negocio. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
