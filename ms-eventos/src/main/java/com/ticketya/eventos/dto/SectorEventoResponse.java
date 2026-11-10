package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoSector;

import java.math.BigDecimal;

public record SectorEventoResponse(
        Long sectorId,
        String nombre,
        TipoSector tipo,
        BigDecimal precio,
        Integer filas,
        Integer asientosPorFila,
        Integer capacidad
) {}
