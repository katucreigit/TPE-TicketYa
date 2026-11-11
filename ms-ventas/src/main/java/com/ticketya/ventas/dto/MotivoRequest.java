package com.ticketya.ventas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema {@code MotivoRequest} de openapi.yaml. */
public record MotivoRequest(
        @NotBlank @Size(max = 255) String motivo
) {
}
