package com.ticketya.ventas.dto;

import com.ticketya.ventas.model.enums.TipoMedioPago;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Schema {@code MedioPago} de openapi.yaml. */
public record MedioPagoRequest(
        @NotNull TipoMedioPago tipo,
        @NotBlank String token,
        @Pattern(regexp = "\\d{4}", message = "debe tener 4 digitos") String ultimos4
) {
}

