package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoSector;

public record SectorResponse(
    Long id,
    String nombre,
    TipoSector tipo,
    Integer capacidad,
    Integer filas,
    Integer asientosPorFila
) {}
