package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.model.enums.TipoEvento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EventoResumenResponse(
        Long id,
        String nombre,
        TipoEvento tipo,
        EstadoEvento estado,
        String ciudad,
        LocalDateTime fechaHora,
        BigDecimal precioDesde,
        List<String> participantes
) {}
