package com.ticketya.recomendaciones.dto;

import com.ticketya.recomendaciones.client.EventoCatalogo;

import java.time.LocalDateTime;
import java.util.List;

/** Un evento comprado por clientes que tambien compraron el evento consultado. */
public record EventoTambienComprado(
        Long eventoId,
        String nombre,
        String tipo,
        String ciudad,
        LocalDateTime fechaHora,
        List<String> participantes,
        long compradoresEnComun
) {
    public static EventoTambienComprado de(EventoCatalogo e, long compradoresEnComun) {
        return new EventoTambienComprado(e.id(), e.nombre(), e.tipo(), e.ciudad(), e.fechaHora(),
                e.participantesONada(), compradoresEnComun);
    }
}
