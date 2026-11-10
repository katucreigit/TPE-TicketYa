package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoParticipante;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ParticipanteRequest(
        @NotBlank(message = "El nombre del participante es obligatorio")
        String nombre,

        @NotNull(message = "El tipo de participante es obligatorio")
        TipoParticipante tipo
) {}
