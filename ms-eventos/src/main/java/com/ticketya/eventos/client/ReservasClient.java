
package com.ticketya.eventos.client;

import com.ticketya.eventos.dto.InventarioRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ReservasClient {

    private final RestClient restClient;

    public ReservasClient(
            @Value("${ticketya.services.reservas-url}") String reservasUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(reservasUrl)
                .build();
    }

    public void crearInventario(InventarioRequest request) {
        restClient.post()
                .uri("/internal/inventarios")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
    public void cerrarInventario(Long eventoId) {
    restClient.post()
            .uri("/internal/eventos/{eventoId}/cerrar", eventoId)
            .retrieve()
            .toBodilessEntity();
}

public void abrirInventario(Long eventoId) {
    restClient.post()
            .uri("/internal/eventos/{eventoId}/abrir", eventoId)
            .retrieve()
            .toBodilessEntity();
}
}

