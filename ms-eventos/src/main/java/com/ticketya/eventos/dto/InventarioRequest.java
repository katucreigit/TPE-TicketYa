package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoSector;

import java.util.List;

public record InventarioRequest(
        Long eventoId,
        Integer maxPorCliente,
        List<Sector> sectores
) {
    public record Sector(
            Long sectorId,
            TipoSector tipo,
            Integer filas,
            Integer asientosPorFila,
            Integer capacidad
    ) {
    }
}
