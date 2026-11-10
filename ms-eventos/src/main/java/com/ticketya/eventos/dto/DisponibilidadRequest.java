package com.ticketya.eventos.dto;

import jakarta.validation.constraints.NotNull;

public record DisponibilidadRequest(
        @NotNull(message = "El campo agotado es obligatorio")
        Boolean agotado
) {}
