package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.model.enums.TipoEvento;

import java.time.LocalDateTime;
import java.util.List;

public record EventoResponse(
        Long id,
        String nombre,
        String descripcion,
        TipoEvento tipo,
        EstadoEvento estado,
        Long organizadorId,
        Long lugarId,
        LocalDateTime fechaHora,
        List<ParticipanteResponse> participantes,
        int maxEntradasPorCliente,
        boolean altaDemanda,
        String motivoCancelacion
) {}
