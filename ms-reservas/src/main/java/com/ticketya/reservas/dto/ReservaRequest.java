package com.ticketya.reservas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Schema {@code ReservaRequest} de openapi.yaml: {@code asientos} en sectores numerados o {@code cantidad}
 * en no numerados. Las reglas que dependen del tipo de sector se validan en el servicio.
 */
public record ReservaRequest(
        @NotNull Long eventoId,
        @NotNull Long sectorId,
        List<@Valid @NotNull AsientoRef> asientos,
        Integer cantidad
) {
}
