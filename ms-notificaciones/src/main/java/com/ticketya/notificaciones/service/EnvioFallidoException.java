package com.ticketya.notificaciones.service;

/** Falla de un intento de envio; el procesador decide si reintenta o deja la notificacion FALLIDA. */
public class EnvioFallidoException extends RuntimeException {

    public EnvioFallidoException(String message) {
        super(message);
    }
}
