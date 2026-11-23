package com.ticketya.fila.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Cuerpo opcional de la activacion; un campo ausente toma el valor por defecto de application.yml. */
public record ActivarFilaRequest(
        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 1000, message = "debe ser como maximo 1000")
        Integer tamanoTanda,
        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 3600, message = "debe ser como maximo 3600")
        Integer segundosEntreTandas
) {
}
