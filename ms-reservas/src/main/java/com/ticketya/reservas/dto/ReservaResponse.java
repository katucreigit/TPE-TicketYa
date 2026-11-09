package com.ticketya.reservas.dto;

import com.ticketya.reservas.model.enums.EstadoReserva;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Schema {@code Reserva} de openapi.yaml. En sectores no numerados {@code items} va vacio. */
public record ReservaResponse(
        UUID reservaId,
        Long eventoId,
        Long sectorId,
        String clienteId,
        EstadoReserva estado,
        int cantidad,
        List<AsientoRef> items,
        LocalDateTime expiraEn
) {
}
