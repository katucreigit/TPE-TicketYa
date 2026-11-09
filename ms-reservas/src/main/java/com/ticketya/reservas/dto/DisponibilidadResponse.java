package com.ticketya.reservas.dto;

import com.ticketya.reservas.model.enums.TipoSector;

import java.util.List;

/** Schema {@code Disponibilidad} de openapi.yaml. */
public record DisponibilidadResponse(Long eventoId, List<Sector> sectores) {

    public record Sector(Long sectorId, TipoSector tipo, int capacidad, long disponibles) {
    }
}
