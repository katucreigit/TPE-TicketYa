package com.ticketya.eventos.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VentasClient {

    private final RestClient restClient;

    public VentasClient(
            @Value("${ticketya.services.ventas-url}") String ventasUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(ventasUrl)
                .build();
    }

    public void procesarCancelacion(Long eventoId) {
        restClient.post()
                .uri("/internal/eventos/{eventoId}/cancelacion", eventoId)
                .retrieve()
                .toBodilessEntity();
    }
}
