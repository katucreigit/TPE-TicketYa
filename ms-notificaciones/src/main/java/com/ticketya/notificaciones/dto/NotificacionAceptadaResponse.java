package com.ticketya.notificaciones.dto;

import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;

/** Respuesta 202 de {@code POST /internal/notificaciones} (schema {@code NotificacionAceptada}). */
public record NotificacionAceptadaResponse(String id, EstadoNotificacion estado) {

    public static NotificacionAceptadaResponse from(Notificacion n) {
        return new NotificacionAceptadaResponse(n.getId(), n.getEstado());
    }
}
