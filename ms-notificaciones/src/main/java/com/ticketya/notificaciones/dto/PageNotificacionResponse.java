package com.ticketya.notificaciones.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Schema {@code PageNotificacion} de openapi.yaml. */
public record PageNotificacionResponse(
        List<NotificacionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageNotificacionResponse from(Page<NotificacionResponse> page) {
        return new PageNotificacionResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
