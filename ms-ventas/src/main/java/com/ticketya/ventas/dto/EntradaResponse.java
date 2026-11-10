package com.ticketya.ventas.dto;

import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;

import java.math.BigDecimal;
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
        EstadoEntrada estado
) {
    public static EntradaResponse from(Entrada entrada) {
        return new EntradaResponse(
                entrada.getId(),
                entrada.getEventoId(),
                entrada.getSectorId(),
                entrada.getFila(),
                entrada.getNumero(),
                entrada.getPrecioUnitario(),
                entrada.getCodigoQr(),
                entrada.getEstado()
        );
    }
}

