package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoParticipante;

public record ParticipanteResponse(
        Long id,
        String nombre,
        TipoParticipante tipo
) {}
