package com.ticketya.ventas.client;

import com.ticketya.ventas.trazabilidad.Correlacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente HTTP best-effort hacia {@code ms-recomendaciones} ({@code POST /internal/compras}), con el mismo enfoque
 * que {@link NotificacionesClient}: timeouts cortos y nunca lanza excepciones ni interrumpe la compra
 * (add-preferencias-recomendaciones, design.md D8). El X-Correlation-Id lo agrega el RestClientCustomizer de
 * {@code PropagacionCorrelacionConfig}, porque el cliente se arma desde el builder inyectado.
 */
@Component
public class RecomendacionesClient {

    private static final Logger log = LoggerFactory.getLogger(RecomendacionesClient.class);

    public record CompraRegistrada(String compraId, String clienteId, List<Long> eventoIds) {
    }

    private final RestClient restClient;

    @Autowired
    public RecomendacionesClient(
            RestClient.Builder builder,
            @Value("${ticketya.services.recomendaciones-url}") String recomendacionesUrl,
            @Value("${ticketya.services.recomendaciones-connect-timeout-ms:500}") int connectTimeoutMs,
            @Value("${ticketya.services.recomendaciones-read-timeout-ms:1000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.baseUrl(recomendacionesUrl).requestFactory(requestFactory).build();
    }

    /** Para pruebas: permite inyectar un {@link RestClient} ya configurado. */
    public RecomendacionesClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Avisa una compra confirmada. Ante cualquier problema (conexion, timeout, respuesta no 2xx) registra
     * {@code WARN RECOMENDACIONES_NO_DISPONIBLE} con el compraId y el correlationId y retorna normalmente.
     */
    public void registrarCompra(String compraId, String clienteId, Long eventoId) {
        try {
            restClient.post()
                    .uri("/internal/compras")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CompraRegistrada(compraId, clienteId, List.of(eventoId)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("RECOMENDACIONES_NO_DISPONIBLE compraId={} clienteId={} eventoId={} causa={} correlationId={}",
                    compraId, Correlacion.limpiar(clienteId), eventoId,
                    Correlacion.limpiar(ex.getClass().getSimpleName() + ": " + ex.getMessage()),
                    Correlacion.actual());
        }
    }
}
