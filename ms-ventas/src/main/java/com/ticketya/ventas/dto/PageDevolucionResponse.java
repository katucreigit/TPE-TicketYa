package com.ticketya.ventas.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Schema {@code PageDevolucion} de openapi.yaml. */
public record PageDevolucionResponse(
        List<DevolucionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageDevolucionResponse from(Page<DevolucionResponse> page) {
        return new PageDevolucionResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
