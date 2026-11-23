package com.ticketya.recomendaciones.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ticketya.recomendaciones.config.RecomendacionesProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente de la busqueda publica de ms-eventos ({@code GET /eventos}). Se construye desde el
 * {@link RestClient.Builder} inyectado, asi que propaga X-Correlation-Id. Cualquier falla (conexion, timeout,
 * respuesta no 2xx) se propaga como excepcion: {@code CatalogoEventos} decide el fallback.
 */
@Component
public class EventosClient {

    /** Pagina de la busqueda (PageEventoResumenResponse). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagina(List<EventoCatalogo> content, int totalPages) {
    }

    public static final int TAMANO_PAGINA = 100;

    private final RestClient restClient;

    public EventosClient(RestClient.Builder builder, RecomendacionesProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getServices().getEventosConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getServices().getEventosReadTimeoutMs());
        this.restClient = builder.baseUrl(properties.getServices().getEventosUrl()).requestFactory(requestFactory).build();
    }

    public Pagina pagina(int numero) {
        Pagina p = restClient.get()
                .uri(uri -> uri.path("/eventos")
                        .queryParam("page", numero)
                        .queryParam("size", TAMANO_PAGINA)
                        .build())
                .retrieve()
                .body(Pagina.class);
        if (p == null || p.content() == null) {
            throw new IllegalStateException("ms-eventos devolvio una pagina vacia o ilegible");
        }
        return p;
    }
}
