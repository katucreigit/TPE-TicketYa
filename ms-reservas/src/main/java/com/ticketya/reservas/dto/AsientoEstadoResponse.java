package com.ticketya.reservas.dto;

import com.ticketya.reservas.model.enums.EstadoAsiento;

/** Schema {@code AsientoEstado} de openapi.yaml. */
public record AsientoEstadoResponse(int fila, int numero, EstadoAsiento estado) {
}
