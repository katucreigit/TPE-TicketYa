package com.ticketya.notificaciones.service;

import com.ticketya.notificaciones.model.Notificacion;

/**
 * Canal por el que se entrega una notificacion. Hoy hay una sola implementacion, {@link EmailSimulado};
 * el push seria otra implementacion de esta misma interfaz.
 */
public interface CanalEnvio {

    /**
     * Envia la notificacion. {@code intento} empieza en 1.
     *
     * @throws EnvioFallidoException si el envio falla y corresponde reintentar
     */
    void enviar(Notificacion notificacion, int intento);
}
