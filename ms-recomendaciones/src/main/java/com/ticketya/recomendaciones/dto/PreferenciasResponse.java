package com.ticketya.recomendaciones.dto;

import com.ticketya.recomendaciones.model.Preferencias;
import com.ticketya.recomendaciones.model.TipoEvento;

import java.time.Instant;
import java.util.List;

/** Preferencias del cliente; {@code ciudad} y {@code actualizadaEn} son null si no las guardo. */
public record PreferenciasResponse(
        String clienteId,
        List<TipoEvento> tipos,
        List<String> artistas,
        String ciudad,
        Instant actualizadaEn
) {
    public static PreferenciasResponse de(Preferencias p) {
        return new PreferenciasResponse(p.getClienteId(), p.getTipos(), p.getArtistas(), p.getCiudad(),
                p.getActualizadaEn());
    }

    public static PreferenciasResponse vacias(String clienteId) {
        return new PreferenciasResponse(clienteId, List.of(), List.of(), null, null);
    }
}
