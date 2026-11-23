package com.ticketya.ventas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Schema {@code Entrada} de openapi.yaml. */
public record EntradaResponse(
        UUID entradaId,
        Long eventoId,
        Long sectorId,
        Integer fila,
        Integer numero,
        BigDecimal precioUnitario,
        String codigoQr,
        EstadoEntrada estado,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime usadaEn,
        @JsonInclude(JsonInclude.Include.NON_NULL) String validadaPor
) {
    public EntradaResponse(UUID entradaId, Long eventoId, Long sectorId, Integer fila, Integer numero,
                           BigDecimal precioUnitario, String codigoQr, EstadoEntrada estado) {
        this(entradaId, eventoId, sectorId, fila, numero, precioUnitario, codigoQr, estado, null, null);
    }

    public static EntradaResponse from(Entrada entrada) {
        return new EntradaResponse(
                entrada.getId(),
                entrada.getEventoId(),
                entrada.getSectorId(),
                entrada.getFila(),
                entrada.getNumero(),
                entrada.getPrecioUnitario(),
                entrada.getCodigoQr(),
                entrada.getEstado(),
                entrada.getUsadaEn(),
                entrada.getValidadaPor()
        );
    }
}
