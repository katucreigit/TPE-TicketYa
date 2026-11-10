package com.ticketya.eventos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record LugarRequest(
    @NotBlank(message = "El nombre es obligatorio")
    String nombre,

    @NotBlank(message = "La dirección es obligatoria")
    String direccion,

    @NotBlank(message = "La ciudad es obligatoria")
    String ciudad,

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    BigDecimal latitud,

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    BigDecimal longitud,

    List<@Valid SectorRequest> sectores
) {}
