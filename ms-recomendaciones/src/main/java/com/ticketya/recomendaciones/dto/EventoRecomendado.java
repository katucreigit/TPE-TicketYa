package com.ticketya.recomendaciones.dto;

import com.ticketya.recomendaciones.service.CalculadoraPuntaje.Motivo;
import com.ticketya.recomendaciones.service.CalculadoraPuntaje.Puntuado;

import java.time.LocalDateTime;
import java.util.List;

/** Un evento recomendado con su puntaje y los motivos que lo explican. */
public record EventoRecomendado(
        Long eventoId,
        String nombre,
        String tipo,
        String ciudad,
        LocalDateTime fechaHora,
        List<String> participantes,
        double puntaje,
        List<Motivo> motivos
) {
    public static EventoRecomendado de(Puntuado p) {
        var e = p.evento();
        return new EventoRecomendado(e.id(), e.nombre(), e.tipo(), e.ciudad(), e.fechaHora(),
                e.participantesONada(), p.puntaje(), p.motivos());
    }
}
