package com.ticketya.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void debeDevolverError503ConLaRutaOriginal() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/notificaciones").build()
        );

        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR,
                Set.of(URI.create("http://localhost/api/notificaciones"))
        );

        ResponseEntity<?> respuesta = controller.servicioNoDisponible(exchange);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(respuesta.getBody()).isNotNull();

        var json = objectMapper.valueToTree(respuesta.getBody());

        assertThat(json.get("code").asText())
                .isEqualTo("SERVICIO_NO_DISPONIBLE");

        assertThat(json.get("path").asText())
                .isEqualTo("/api/notificaciones");
    }
}