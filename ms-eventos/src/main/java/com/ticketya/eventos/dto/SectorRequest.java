package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoSector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SectorRequest(
    @NotBlank(message = "El nombre del sector es obligatorio")
    String nombre,

    @NotNull(message = "El tipo de sector es obligatorio")
    TipoSector tipo,

    @Positive(message = "La capacidad debe ser mayor a cero")
    Integer capacidad,

    @Positive(message = "La cantidad de filas debe ser mayor a cero")
    Integer filas,

    @Positive(message = "Los asientos por fila deben ser mayores a cero")
    Integer asientosPorFila
) {}