package com.ticketya.eventos.dto;

import jakarta.validation.constraints.NotBlank;

public record MotivoRequest(
        @NotBlank(message = "El motivo de cancelación es obligatorio")
        String motivo
) {}
