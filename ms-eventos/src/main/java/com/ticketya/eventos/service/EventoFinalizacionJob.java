package com.ticketya.eventos.service;

import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.repository.EventoRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class EventoFinalizacionJob {

    private final EventoRepository eventoRepository;

    public EventoFinalizacionJob(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void finalizarEventosPasados() {

        List<Evento> eventos = eventoRepository.findByEstadoInAndFechaHoraBefore(
                List.of(EstadoEvento.PUBLICADO, EstadoEvento.AGOTADO),
                LocalDateTime.now()
        );

        for (Evento evento : eventos) {
            evento.setEstado(EstadoEvento.FINALIZADO);
        }

        eventoRepository.saveAll(eventos);
    }
}
