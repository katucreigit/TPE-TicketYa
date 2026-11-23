package com.ticketya.recomendaciones.dto;

import java.util.List;

/** {@code degradado=true} si ms-eventos no respondio y se uso la ultima copia del catalogo o una lista vacia. */
public record RecomendacionesResponse(boolean degradado, List<EventoRecomendado> eventos) {
}
