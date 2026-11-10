package com.ticketya.eventos.service;

import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.repository.EventoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class EventoFinalizacionJobTest {

    @Test
    void jobPuedeEjecutarseVariasVecesSinVolverAFinalizarEventos() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        EventoFinalizacionJob job = new EventoFinalizacionJob(eventoRepository);

        Evento evento = new Evento();
        evento.setEstado(EstadoEvento.PUBLICADO);

        when(eventoRepository.findByEstadoInAndFechaHoraBefore(
                anyList(), any(LocalDateTime.class)
        )).thenReturn(List.of(evento), List.of());

        job.finalizarEventosPasados();
        job.finalizarEventosPasados();

        org.junit.jupiter.api.Assertions.assertEquals(
                EstadoEvento.FINALIZADO,
                evento.getEstado()
        );

        verify(eventoRepository, times(2))
                .findByEstadoInAndFechaHoraBefore(anyList(), any(LocalDateTime.class));

        verify(eventoRepository, times(2)).saveAll(anyList());
    }
}
