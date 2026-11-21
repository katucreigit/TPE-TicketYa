package com.ticketya.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.gateway.GatewayErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void debePermitirRegistrarUnaSolicitudAnonima() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/eventos")
                        .header("X-Correlation-Id", "compra-123")
                        .build()
        );

        AtomicReference<ServerWebExchange> exchangeRecibido = new AtomicReference<>();

        filter.filter(exchange, exchangeMutado -> {
            exchangeRecibido.set(exchangeMutado);
            exchangeMutado.getResponse().setStatusCode(HttpStatus.OK);
            return exchangeMutado.getResponse().setComplete();
        }).block();

        assertThat(exchangeRecibido.get()).isNotNull();
        assertThat(exchangeRecibido.get().getRequest().getMethod().name())
                .isEqualTo("GET");
        assertThat(exchangeRecibido.get().getRequest().getURI().getPath())
                .isEqualTo("/api/eventos");
    }

    @Test
    void debePermitirRegistrarUnaSolicitudAutenticada() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/compras")
                        .header("X-User-Id", "15")
                        .header("X-Correlation-Id", "compra-456")
                        .build()
        );

        AtomicReference<ServerWebExchange> exchangeRecibido = new AtomicReference<>();

        filter.filter(exchange, exchangeMutado -> {
            exchangeRecibido.set(exchangeMutado);
            exchangeMutado.getResponse().setStatusCode(HttpStatus.CREATED);
            return exchangeMutado.getResponse().setComplete();
        }).block();

        assertThat(exchangeRecibido.get()).isNotNull();
        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst("X-User-Id")).isEqualTo("15");
        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst("X-Correlation-Id")).isEqualTo("compra-456");
    }

    @Test
    void debeRegistrarElEstado503CuandoFallaLaConexionConUnServicio() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/eventos")
                        .header("X-Correlation-Id", "compra-789")
                        .build()
        );

        GatewayErrorHandler errorHandler =
                new GatewayErrorHandler(new ObjectMapper().findAndRegisterModules());

        filter.filter(exchange, exchangeMutado ->
                errorHandler.filter(exchangeMutado, ignorado ->
                        Mono.error(new ConnectException("Servicio no disponible"))
                )
        ).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
