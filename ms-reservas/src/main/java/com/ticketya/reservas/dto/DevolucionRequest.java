package com.ticketya.reservas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Cuerpo de POST /internal/reservas/{id}/devolver: {@code items} (numerados) o {@code cantidad} (no numerados). */
public record DevolucionRequest(
        List<@Valid @NotNull AsientoRef> items,
        Integer cantidad
) {
}
