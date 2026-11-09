package com.ticketya.reservas.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Comparator;

/** Schema {@code AsientoRef} de openapi.yaml. */
public record AsientoRef(@NotNull Integer fila, @NotNull Integer numero) {

    /** Orden fijo por fila y numero: todos los UPDATE de asientos se ejecutan en este orden. */
    public static final Comparator<AsientoRef> POR_FILA_Y_NUMERO =
            Comparator.comparing(AsientoRef::fila).thenComparing(AsientoRef::numero);
}
