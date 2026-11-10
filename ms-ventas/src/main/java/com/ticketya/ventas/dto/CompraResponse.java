package com.ticketya.ventas.dto;

import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.enums.EstadoCompra;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Schema {@code Compra} de openapi.yaml. */
public record CompraResponse(
        UUID compraId,
        EstadoCompra estado,
        BigDecimal total,
        MedioPagoResponse medioPago,
        List<EntradaResponse> entradas
) {
    public static CompraResponse from(Compra compra) {
        List<EntradaResponse> entradas = compra.getEntradas() == null
                ? List.of()
                : compra.getEntradas().stream().map(EntradaResponse::from).toList();
        return new CompraResponse(
                compra.getId(),
                compra.getEstado(),
                compra.getTotal(),
                MedioPagoResponse.from(compra.getPago()),
                entradas
        );
    }
}

