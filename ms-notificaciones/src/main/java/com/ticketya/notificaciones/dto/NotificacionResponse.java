package com.ticketya.notificaciones.dto;

import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;

import java.time.Instant;
import java.util.Map;

/** Detalle de una notificacion (schema {@code Notificacion} de openapi.yaml). */
public record NotificacionResponse(
        String id,
        TipoNotificacion tipo,
        DestinatarioDto destinatario,
        Map<String, Object> datos,
        EstadoNotificacion estado,
        int intentos,
        String ultimoError,
        String claveDeduplicacion,
        String correlationId,
        Instant creadaEn,
        Instant actualizadaEn,
        Instant proximoIntentoEn,
        Instant enviadaEn
) {
    public static NotificacionResponse from(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getTipo(), DestinatarioDto.from(n.getDestinatario()),
                n.getDatos() == null ? Map.of() : n.getDatos(), n.getEstado(), n.getIntentos(), n.getUltimoError(),
                n.getClaveDeduplicacion(), n.getCorrelationId(), n.getCreadaEn(), n.getActualizadaEn(),
                n.getProximoIntentoEn(), n.getEnviadaEn());
    }
}
