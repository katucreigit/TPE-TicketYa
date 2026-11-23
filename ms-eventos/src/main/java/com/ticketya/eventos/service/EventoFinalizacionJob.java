package com.ticketya.eventos.service;

import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.repository.EventoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class EventoFinalizacionJob {

    private final EventoRepository eventoRepository;
    private CacheManager cacheManager;

    public EventoFinalizacionJob(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Autowired
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void finalizarEventosPasados() {

        List<Evento> eventos = eventoRepository.findByEstadoInAndFechaHoraBefore(
                List.of(EstadoEvento.PUBLICADO, EstadoEvento.AGOTADO),
                LocalDateTime.now()
        );

        if (eventos.isEmpty()) {
            return;
        }

        for (Evento evento : eventos) {
            evento.setEstado(EstadoEvento.FINALIZADO);
        }

        eventoRepository.saveAll(eventos);

        invalidarCatalogoYDetalle();
    }

    private void invalidarCatalogoYDetalle() {
        if (cacheManager == null) {
            return;
        }

        Cache catalogo = cacheManager.getCache("catalogoEventos");
        if (catalogo != null) {
            catalogo.clear();
        }

        Cache detalle = cacheManager.getCache("detalleEvento");
        if (detalle != null) {
            detalle.clear();
        }
    }
}
