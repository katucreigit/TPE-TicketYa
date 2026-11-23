package com.ticketya.recomendaciones.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Un evento del catalogo, tal como lo devuelve {@code GET /eventos} de ms-eventos (EventoResumenResponse).
 * {@code tipo} y {@code estado} quedan como texto para que un valor nuevo en ms-eventos no rompa la lectura.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoCatalogo(
        Long id,
        String nombre,
        String tipo,
        String estado,
        String ciudad,
        LocalDateTime fechaHora,
        List<String> participantes
) {
    public List<String> participantesONada() {
        return participantes == null ? List.of() : participantes;
    }
}
