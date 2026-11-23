package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.client.EventoCatalogo;
import com.ticketya.recomendaciones.dto.EventoRecomendado;
import com.ticketya.recomendaciones.dto.EventoTambienComprado;
import com.ticketya.recomendaciones.dto.RecomendacionesResponse;
import com.ticketya.recomendaciones.dto.TambienCompraronResponse;
import com.ticketya.recomendaciones.repository.CompraRepository;
import com.ticketya.recomendaciones.service.CalculadoraPuntaje.Puntuado;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recomendaciones personalizadas y "tambien compraron" (design.md D3 y D4). Los candidatos son siempre los eventos
 * PUBLICADO del catalogo; las compras y preferencias solo ordenan.
 */
@Service
public class RecomendacionService {

    private final CatalogoEventos catalogo;
    private final CompraRepository compras;
    private final PreferenciasService preferencias;

    public RecomendacionService(CatalogoEventos catalogo, CompraRepository compras, PreferenciasService preferencias) {
        this.catalogo = catalogo;
        this.compras = compras;
        this.preferencias = preferencias;
    }

    public RecomendacionesResponse recomendar(String clienteId, int limite) {
        CatalogoEventos.Resultado cat = catalogo.obtener();
        Set<Long> comprados = compras.eventosDe(clienteId);
        List<EventoCatalogo> candidatos = cat.eventos().stream().filter(e -> !comprados.contains(e.id())).toList();
        if (candidatos.isEmpty()) {
            return new RecomendacionesResponse(cat.degradado(), List.of());
        }
        Map<Long, Long> compradores = compras.compradoresPorEvento();
        Map<Long, Long> enComun = compras.compradoresEnComun(comprados, clienteId);
        long maxCompradores = maximo(candidatos, compradores);
        long maxEnComun = maximo(candidatos, enComun);

        CalculadoraPuntaje calculadora = new CalculadoraPuntaje(preferencias.buscar(clienteId));
        List<EventoRecomendado> eventos = candidatos.stream()
                .map(e -> calculadora.puntuar(e,
                        compradores.getOrDefault(e.id(), 0L), maxCompradores,
                        enComun.getOrDefault(e.id(), 0L), maxEnComun))
                .sorted(CalculadoraPuntaje.ORDEN)
                .limit(limite)
                .map(EventoRecomendado::de)
                .toList();
        return new RecomendacionesResponse(cat.degradado(), eventos);
    }

    public TambienCompraronResponse tambienCompraron(Long eventoId, int limite) {
        CatalogoEventos.Resultado cat = catalogo.obtener();
        Map<Long, Long> enComun = compras.compradoresEnComun(Set.of(eventoId), null);
        List<EventoTambienComprado> eventos = cat.eventos().stream()
                .filter(e -> !e.id().equals(eventoId) && enComun.getOrDefault(e.id(), 0L) > 0)
                .sorted(Comparator.<EventoCatalogo>comparingLong(e -> enComun.get(e.id())).reversed()
                        .thenComparing(EventoCatalogo::fechaHora, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                        .thenComparing(EventoCatalogo::id))
                .limit(limite)
                .map(e -> EventoTambienComprado.de(e, enComun.get(e.id())))
                .toList();
        return new TambienCompraronResponse(eventoId, cat.degradado(), eventos);
    }

    private static long maximo(List<EventoCatalogo> candidatos, Map<Long, Long> conteo) {
        return candidatos.stream().mapToLong(e -> conteo.getOrDefault(e.id(), 0L)).max().orElse(0);
    }
}
