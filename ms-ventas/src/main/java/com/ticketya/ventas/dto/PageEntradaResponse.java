package com.ticketya.ventas.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Schema {@code PageEntrada} de openapi.yaml. */
public record PageEntradaResponse(
        List<EntradaResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageEntradaResponse from(Page<EntradaResponse> page) {
        return new PageEntradaResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

