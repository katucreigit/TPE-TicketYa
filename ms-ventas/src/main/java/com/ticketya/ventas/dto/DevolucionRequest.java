package com.ticketya.ventas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Schema {@code DevolucionRequest} de openapi.yaml. */
public record DevolucionRequest(
        @NotNull UUID entradaId,
        @Size(max = 255) String motivo
) {
}
