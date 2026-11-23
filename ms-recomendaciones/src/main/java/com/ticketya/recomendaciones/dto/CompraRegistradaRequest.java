package com.ticketya.recomendaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Cuerpo de POST /internal/compras, que envia ms-ventas al confirmar una compra. */
public record CompraRegistradaRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "admite como maximo 100 caracteres")
        String compraId,
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "admite como maximo 100 caracteres")
        String clienteId,
        @NotEmpty(message = "debe tener al menos un evento") @Size(max = 50, message = "admite como maximo 50 eventos")
        List<@NotNull(message = "no puede ser nulo") @Positive(message = "debe ser positivo") Long> eventoIds
) {
}
