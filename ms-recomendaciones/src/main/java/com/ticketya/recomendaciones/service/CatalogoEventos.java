package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.client.EventoCatalogo;
import com.ticketya.recomendaciones.client.EventosClient;
import com.ticketya.recomendaciones.config.RecomendacionesProperties;
import com.ticketya.recomendaciones.trazabilidad.Correlacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Catalogo de eventos PUBLICADO con una copia en memoria y fallback (design.md D5):
 * <ul>
 *   <li>copia con menos de {@code ttl}: se usa sin consultar ({@code degradado=false});</li>
 *   <li>si no, se lee todo el catalogo de ms-eventos y reemplaza la copia ({@code degradado=false});</li>
 *   <li>si ms-eventos falla en cualquier pagina: la ultima copia aunque haya vencido, o una lista vacia, con
 *   {@code degradado=true}. Nunca se guarda una copia parcial.</li>
 * </ul>
 */
@Component
public class CatalogoEventos {

    private static final Logger log = LoggerFactory.getLogger(CatalogoEventos.class);
    private static final String PUBLICADO = "PUBLICADO";

    public record Resultado(List<EventoCatalogo> eventos, boolean degradado) {
    }

    private record Copia(List<EventoCatalogo> eventos, Instant obtenidaEn) {
    }

    private final EventosClient eventosClient;
    private final Clock clock;
    private final Duration ttl;
    private final int maxPaginas;
    private final AtomicReference<Copia> copia = new AtomicReference<>();

    public CatalogoEventos(EventosClient eventosClient, Clock clock, RecomendacionesProperties properties) {
        this.eventosClient = eventosClient;
        this.clock = clock;
        this.ttl = properties.getCatalogo().getTtl();
        this.maxPaginas = Math.max(1, properties.getCatalogo().getMaxPaginas());
    }

    public Resultado obtener() {
        Copia actual = copia.get();
        Instant ahora = clock.instant();
        if (actual != null && Duration.between(actual.obtenidaEn(), ahora).compareTo(ttl) < 0) {
            return new Resultado(actual.eventos(), false);
        }
        try {
            List<EventoCatalogo> publicados = leerPublicados();
            copia.set(new Copia(publicados, ahora));
            return new Resultado(publicados, false);
        } catch (RuntimeException e) {
            log.warn("CATALOGO_NO_DISPONIBLE copiaEnMemoria={} causa={} correlationId={}",
                    actual != null, Correlacion.limpiar(e.getClass().getSimpleName() + ": " + e.getMessage()),
                    Correlacion.actual());
            return new Resultado(actual == null ? List.of() : actual.eventos(), true);
        }
    }

    /** Vacia la copia en memoria. Lo usan las pruebas para empezar cada caso sin catalogo. */
    public void olvidar() {
        copia.set(null);
    }

    private List<EventoCatalogo> leerPublicados() {
        List<EventoCatalogo> publicados = new ArrayList<>();
        int pagina = 0;
        int totalPaginas;
        do {
            EventosClient.Pagina p = eventosClient.pagina(pagina);
            p.content().stream().filter(e -> PUBLICADO.equals(e.estado()) && e.id() != null).forEach(publicados::add);
            totalPaginas = p.totalPages();
            pagina++;
        } while (pagina < totalPaginas && pagina < maxPaginas);
        if (pagina < totalPaginas) {
            log.warn("CATALOGO_TRUNCADO paginasLeidas={} totalPaginas={}", pagina, totalPaginas);
        }
        return List.copyOf(publicados);
    }
}
