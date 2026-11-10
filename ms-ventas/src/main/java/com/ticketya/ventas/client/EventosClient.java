package com.ticketya.ventas.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.client.EventoRemoto.SectorPrecio;
import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cliente HTTP de ms-eventos para consultar estado, fecha del evento y precios por sector al comprar.
 */
@Component
public class EventosClient {

    private static final ParameterizedTypeReference<List<SectorPrecio>> LISTA_SECTORES =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventosClient(RestClient.Builder builder,
                         ObjectMapper objectMapper,
                         @Value("${ticketya.services.eventos-url}") String eventosUrl,
                         @Value("${ticketya.services.eventos-timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = builder.baseUrl(eventosUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    /** Para pruebas: permite inyectar un {@link RestClient} ya configurado. */
    EventosClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Obtiene el estado, la fecha/hora y los precios por sector de un evento en ms-eventos.
     *
     * @throws NotFoundException   (404) el evento no existe (o esta en BORRADOR)
     * @throws BadGatewayException (502) falla de comunicacion o respuesta invalida de ms-eventos
     */
    public EventoRemoto obtenerEvento(Long eventoId) {
        EventoHeader header = ejecutar(() -> restClient.get()
                .uri("/eventos/{id}", eventoId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(EventoHeader.class));
        if (header == null || header.estado() == null || header.fechaHora() == null) {
            throw new BadGatewayException("ms-eventos respondio con datos incompletos del evento " + eventoId);
        }

        List<SectorPrecio> sectores = ejecutar(() -> restClient.get()
                .uri("/eventos/{id}/sectores", eventoId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(LISTA_SECTORES));
        if (sectores == null) {
            throw new BadGatewayException("ms-eventos respondio sin sectores para el evento " + eventoId);
        }

        return new EventoRemoto(header.id() != null ? header.id() : eventoId,
                header.estado(), header.fechaHora(), List.copyOf(sectores));
    }

    /**
     * Obtiene y valida el snapshot necesario para comprar: verifica que el evento admita compras
     * (PUBLICADO o AGOTADO) y que el sector tenga precio configurado.
     *
     * @throws NotFoundException   (404) el evento o el sector no existen
     * @throws ConflictException   (409 EVENTO_NO_DISPONIBLE) el evento no admite compras (ej. SUSPENDIDO, CANCELADO)
     * @throws BadGatewayException (502) falla de comunicacion con ms-eventos
     */
    public SnapshotCompra obtenerSnapshotParaCompra(Long eventoId, Long sectorId) {
        EventoRemoto evento = obtenerEvento(eventoId);
        if (!evento.admiteCompra()) {
            throw new ConflictException("EVENTO_NO_DISPONIBLE",
                    "El evento " + eventoId + " no admite compras (estado " + evento.estado() + ")");
        }
        BigDecimal precio = evento.precioDeSector(sectorId);
        if (precio == null) {
            throw new NotFoundException("El sector " + sectorId + " no tiene precio en el evento " + eventoId);
        }
        return new SnapshotCompra(evento.id(), sectorId, evento.fechaHora(), precio);
    }

    private record EventoHeader(Long id, String estado, LocalDateTime fechaHora) {
    }

    private <T> T ejecutar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            throw new BadGatewayException("No se pudo comunicar con ms-eventos: " + e.getMessage());
        }
    }

    private void traducirError(HttpRequest request, ClientHttpResponse response) throws IOException {
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
        String detalle = mensaje != null ? mensaje : "ms-eventos respondio " + status;

        switch (status) {
            case 404 -> throw new NotFoundException(detalle);
            case 409 -> throw new ConflictException(codigo != null ? codigo : "ESTADO_INVALIDO", detalle);
            default -> throw new BadGatewayException("ms-eventos respondio " + status + ": " + detalle);
        }
    }
}

