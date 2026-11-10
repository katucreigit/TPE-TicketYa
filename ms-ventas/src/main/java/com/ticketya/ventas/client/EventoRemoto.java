package com.ticketya.ventas.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos de un evento y sus precios por sector obtenidos de ms-eventos
 * ({@code GET /eventos/{id}} y {@code GET /eventos/{id}/sectores}).
 */
public record EventoRemoto(
        Long id,
        String estado,
        LocalDateTime fechaHora,
        List<SectorPrecio> sectores
) {

    public record SectorPrecio(Long sectorId, BigDecimal precio) {
    }

    /**
     * Snapshot congelado al momento de comprar (precio unitario del sector y fecha/hora del evento).
     */
    public record SnapshotCompra(
            Long eventoId,
            Long sectorId,
            LocalDateTime fechaHoraEvento,
            BigDecimal precioUnitario
    ) {
    }

    /**
     * Un evento admite confirmar una reserva ya tomada si esta PUBLICADO o AGOTADO
     * (queda AGOTADO cuando la propia reserva tomo el ultimo lugar disponible).
     */
    public boolean admiteCompra() {
        return "PUBLICADO".equals(estado) || "AGOTADO".equals(estado);
    }

    public BigDecimal precioDeSector(Long sectorId) {
        if (sectores == null || sectorId == null) {
            return null;
        }
        return sectores.stream()
                .filter(s -> sectorId.equals(s.sectorId()))
                .map(SectorPrecio::precio)
                .findFirst()
                .orElse(null);
    }
}

