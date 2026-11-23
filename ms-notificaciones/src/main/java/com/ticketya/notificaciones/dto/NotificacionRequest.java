package com.ticketya.notificaciones.dto;

import com.ticketya.notificaciones.model.TipoNotificacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Cuerpo de {@code POST /internal/notificaciones} (schema {@code NotificacionRequest} de openapi.yaml). */
public record NotificacionRequest(
        @NotNull TipoNotificacion tipo,
        @NotNull @Valid DestinatarioDto destinatario,
        Map<String, Object> datos,
        @Size(max = 200) String claveDeduplicacion
) {
}
