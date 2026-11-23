
package com.ticketya.eventos.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.repository.EventoRepository;

class EventoFinalizacionJobTest {

    @Test
    void jobPuedeEjecutarseVariasVecesSinVolverAFinalizarEventos() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        EventoFinalizacionJob job = new EventoFinalizacionJob(eventoRepository);

        CacheManager cacheManager = mock(CacheManager.class);
        Cache catalogo = mock(Cache.class);
        Cache detalle = mock(Cache.class);

        when(cacheManager.getCache("catalogoEventos")).thenReturn(catalogo);
        when(cacheManager.getCache("detalleEvento")).thenReturn(detalle);

        job.setCacheManager(cacheManager);

        Evento evento = new Evento();
        evento.setEstado(EstadoEvento.PUBLICADO);

        when(eventoRepository.findByEstadoInAndFechaHoraBefore(
                anyList(), any(LocalDateTime.class)
        )).thenReturn(List.of(evento), List.of());

        job.finalizarEventosPasados();
        job.finalizarEventosPasados();

        assertEquals(EstadoEvento.FINALIZADO, evento.getEstado());

        verify(eventoRepository, times(2))
                .findByEstadoInAndFechaHoraBefore(anyList(), any(LocalDateTime.class));

        verify(eventoRepository, times(1)).saveAll(anyList());

        verify(catalogo, times(1)).clear();
        verify(detalle, times(1)).clear();
    }

    @Test
    void noInvalidaLasCachesCuandoNoHayEventosParaFinalizar() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        EventoFinalizacionJob job = new EventoFinalizacionJob(eventoRepository);

        CacheManager cacheManager = mock(CacheManager.class);
        job.setCacheManager(cacheManager);

        when(eventoRepository.findByEstadoInAndFechaHoraBefore(
                anyList(), any(LocalDateTime.class)
        )).thenReturn(List.of());

        job.finalizarEventosPasados();

        verify(eventoRepository, never()).saveAll(anyList());
        verifyNoInteractions(cacheManager);
    }
}