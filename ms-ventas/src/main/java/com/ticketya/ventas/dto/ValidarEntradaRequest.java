package com.ticketya.ventas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Schema {@code ValidarEntradaRequest} de openapi.yaml. */
public record ValidarEntradaRequest(
        @NotBlank String codigoQr,
        @NotNull Long eventoId
) {
}

