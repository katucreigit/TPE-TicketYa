package com.ticketya.recomendaciones.dto;

import java.util.List;

/** Respuesta de "quienes compraron X tambien compraron Y". */
public record TambienCompraronResponse(Long eventoId, boolean degradado, List<EventoTambienComprado> eventos) {
}
