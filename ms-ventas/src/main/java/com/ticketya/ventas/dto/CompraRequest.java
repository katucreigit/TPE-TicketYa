package com.ticketya.ventas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Schema {@code CompraRequest} de openapi.yaml. */
public record CompraRequest(
        @NotNull UUID reservaId,
        @NotNull @Valid MedioPagoRequest medioPago
) {
}

