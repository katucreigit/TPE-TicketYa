package com.ticketya.ventas.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Schema {@code PageCompra} de openapi.yaml. */
public record PageCompraResponse(
        List<CompraResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageCompraResponse from(Page<CompraResponse> page) {
        return new PageCompraResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

