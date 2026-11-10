package com.ticketya.ventas.client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reserva tal como la devuelve ms-reservas (schema {@code Reserva} de openapi.yaml).
 * {@code expiraEn} esta en UTC, el huso de sesion de ms-reservas. En sectores no numerados {@code items} va vacio.
 */
public record ReservaRemota(
        UUID reservaId,
        Long eventoId,
        Long sectorId,
        String clienteId,
        String estado,
        int cantidad,
        List<Item> items,
        LocalDateTime expiraEn
) {

    public record Item(int fila, int numero) {
    }
}

