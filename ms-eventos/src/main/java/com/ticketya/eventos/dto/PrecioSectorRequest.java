package com.ticketya.eventos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PrecioSectorRequest(

        @NotNull(message = "El ID del sector es obligatorio")
        Long sectorId,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false,
                message = "El precio debe ser mayor a cero")
        BigDecimal precio

) {}
