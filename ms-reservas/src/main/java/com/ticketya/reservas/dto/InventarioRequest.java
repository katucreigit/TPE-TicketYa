package com.ticketya.reservas.dto;

import com.ticketya.reservas.model.enums.TipoSector;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Schema {@code InventarioRequest} de openapi.yaml. Las reglas que dependen del tipo de sector
 * (filas, asientosPorFila, capacidad) y los sectorId repetidos se validan en el servicio.
 */
public record InventarioRequest(
        @NotNull Long eventoId,
        @Min(1) Integer maxPorCliente,
        @NotEmpty List<@Valid @NotNull Sector> sectores
) {
    public record Sector(
            @NotNull Long sectorId,
            @NotNull TipoSector tipo,
            Integer filas,
            Integer asientosPorFila,
            Integer capacidad
    ) {
    }
}
