package com.ticketya.reservas.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Cliente de los endpoints internos de ms-eventos.
 * Cualquier respuesta no 2xx o falla de conexion se propaga como excepcion; quien llama decide si reintentar.
 */
@Component
public class EventosClient {

    private final RestClient restClient;

    public EventosClient(RestClient.Builder builder,
                         @Value("${ticketya.services.eventos-url}") String eventosUrl,
                         @Value("${ticketya.services.eventos-timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = builder.baseUrl(eventosUrl).requestFactory(requestFactory).build();
    }

    /** PUT /internal/eventos/{id}/disponibilidad: agotado=true pasa el evento a AGOTADO y false a PUBLICADO. */
    public void actualizarDisponibilidad(Long eventoId, boolean agotado) {
        restClient.put()
                .uri("/internal/eventos/{id}/disponibilidad", eventoId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("agotado", agotado))
                .retrieve()
                .toBodilessEntity();
    }
}
