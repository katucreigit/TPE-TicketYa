package com.ticketya.ventas.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

/**
 * Cliente de ms-reservas para la saga de compra: validar la reserva, confirmarla y liberarla.
 * Las respuestas de error se traducen a las excepciones de ms-ventas (404, 403, 409, 410); una falla de
 * conexion, un timeout o cualquier otro error del servicio se informa como {@link BadGatewayException} (502).
 */
@Component
public class ReservasClient {

    private static final String HEADER_ID = "X-User-Id";
    private static final String HEADER_ROL = "X-User-Rol";
    private static final String ROL_CLIENTE = "CLIENTE";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReservasClient(RestClient.Builder builder,
                          ObjectMapper objectMapper,
                          @Value("${ticketya.services.reservas-url}") String reservasUrl,
                          @Value("${ticketya.services.reservas-timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = builder.baseUrl(reservasUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    /** Para pruebas: permite inyectar un {@link RestClient} ya configurado. */
    ReservasClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta la reserva y verifica que el cliente sea el dueno y que siga PENDIENTE. La vigencia la decide
     * ms-reservas con el reloj de su base: una PENDIENTE ya expirada se informa como VENCIDA. ms-ventas no compara
     * expiraEn contra un reloj propio.
     *
     * @throws NotFoundException   (404) la reserva no existe
     * @throws ForbiddenException  (403) pertenece a otro cliente
     * @throws GoneException       (410) ms-reservas la informa VENCIDA
     * @throws ConflictException   (409 ESTADO_INVALIDO) esta LIBERADA o CONFIRMADA
     */
    public ReservaRemota validar(UUID reservaId, String clienteId) {
        ReservaRemota reserva = ejecutar(() -> restClient.get()
                .uri("/reservas/{id}", reservaId)
                .header(HEADER_ID, clienteId)
                .header(HEADER_ROL, ROL_CLIENTE)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(ReservaRemota.class));
        if (reserva == null) {
            throw new BadGatewayException("ms-reservas respondio sin cuerpo");
        }

        String estado = reserva.estado();
        if ("VENCIDA".equals(estado)) {
            throw new GoneException("La reserva vencio. No se realizo ningun cobro.");
        }
        if ("LIBERADA".equals(estado) || "CONFIRMADA".equals(estado)) {
            throw ConflictException.estadoInvalido("La reserva " + reservaId + " esta " + estado);
        }
        return reserva;
    }

    /**
     * POST /internal/reservas/{id}/confirmar. Es idempotente en ms-reservas (confirmar de nuevo no cambia nada).
     *
     * @throws GoneException     (410) la reserva vencio
     * @throws ConflictException (409) la reserva ya fue liberada
     * @throws NotFoundException (404) la reserva no existe
     */
    public void confirmar(UUID reservaId) {
        postInterno("/internal/reservas/{id}/confirmar", reservaId);
    }

    /**
     * POST /internal/reservas/{id}/liberar. Idempotente: liberar una reserva ya liberada o vencida no falla.
     *
     * @throws ConflictException (409) la reserva ya esta CONFIRMADA
     * @throws NotFoundException (404) la reserva no existe
     */
    public void liberar(UUID reservaId) {
        postInterno("/internal/reservas/{id}/liberar", reservaId);
    }

    private void postInterno(String uri, UUID reservaId) {
        ejecutar(() -> {
            restClient.post()
                    .uri(uri, reservaId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::traducirError)
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            throw new BadGatewayException("No se pudo comunicar con ms-reservas: " + e.getMessage());
        }
    }

    /** Convierte una respuesta de error de ms-reservas en la excepcion equivalente de ms-ventas. */
    private void traducirError(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String codigo = null;
        String mensaje = null;
        try {
            JsonNode cuerpo = objectMapper.readTree(response.getBody());
            codigo = cuerpo.path("code").asText(null);
            mensaje = cuerpo.path("message").asText(null);
        } catch (IOException | RuntimeException e) {
            // Cuerpo ausente o que no es JSON: se usa el mensaje por defecto.
        }
        String detalle = mensaje != null ? mensaje : "ms-reservas respondio " + status;

        switch (status) {
            case 403 -> throw new ForbiddenException();
            case 404 -> throw new NotFoundException(detalle);
            case 409 -> throw new ConflictException(codigo != null ? codigo : "ESTADO_INVALIDO", detalle);
            case 410 -> throw new GoneException(detalle);
            default -> throw new BadGatewayException("ms-reservas respondio " + status + ": " + detalle);
        }
    }
}

