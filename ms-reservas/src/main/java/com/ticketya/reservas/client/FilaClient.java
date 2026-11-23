package com.ticketya.reservas.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente de la consulta interna de ms-fila (add-fila-virtual, design.md D10). Se construye desde el
 * {@link RestClient.Builder} inyectado, asi que hereda la propagacion de X-Correlation-Id.
 * <p>
 * Nunca lanza: cualquier falla (conexion rechazada, timeout, respuesta no 2xx o cuerpo ilegible) vuelve como
 * {@link Estado#noDisponible(String)} y quien llama decide; ms-reservas aplica fail-open.
 */
@Component
public class FilaClient {

    /** Respuesta de ms-fila, o la causa si no se pudo obtener. */
    public record Estado(boolean disponible, boolean activa, boolean habilitado, String causa) {

        static Estado noDisponible(String causa) {
            return new Estado(false, false, true, causa);
        }

        /** La fila esta activa y el cliente no esta habilitado: no puede reservar. */
        public boolean bloquea() {
            return disponible && activa && !habilitado;
        }
    }

    private record Respuesta(Boolean activa, Boolean habilitado) {
    }

    private final RestClient restClient;

    public FilaClient(RestClient.Builder builder,
                      @Value("${ticketya.services.fila-url}") String filaUrl,
                      @Value("${ticketya.services.fila-timeout-ms:1000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = builder.baseUrl(filaUrl).requestFactory(requestFactory).build();
    }

    /** GET /internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado. */
    public Estado estado(Long eventoId, String clienteId) {
        try {
            Respuesta r = restClient.get()
                    .uri("/internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado", eventoId, clienteId)
                    .retrieve()
                    .body(Respuesta.class);
            if (r == null || r.activa() == null || r.habilitado() == null) {
                return Estado.noDisponible("respuesta incompleta de ms-fila");
            }
            return new Estado(true, r.activa(), r.habilitado(), null);
        } catch (RuntimeException e) {
            return Estado.noDisponible(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
