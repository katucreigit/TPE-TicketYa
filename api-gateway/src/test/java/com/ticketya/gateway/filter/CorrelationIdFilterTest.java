package com.ticketya.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void debeGenerarCorrelationIdCuandoNoExiste() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/eventos")
                        .build()
        );

        AtomicReference<ServerWebExchange> exchangeRecibido = new AtomicReference<>();

        filter.filter(exchange, exchangeMutado -> {
            exchangeRecibido.set(exchangeMutado);
            return exchangeMutado.getResponse().setComplete();
        }).block();

        String correlationId = exchangeRecibido.get()
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdFilter.HEADER_CORRELATION_ID);

        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.HEADER_CORRELATION_ID))
                .isEqualTo(correlationId);
    }

    @Test
    void debeConservarCorrelationIdExistente() {
        String correlationId = "compra-123-abc";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/eventos")
                        .header(CorrelationIdFilter.HEADER_CORRELATION_ID, correlationId)
                        .build()
        );

        AtomicReference<ServerWebExchange> exchangeRecibido = new AtomicReference<>();

        filter.filter(exchange, exchangeMutado -> {
            exchangeRecibido.set(exchangeMutado);
            return exchangeMutado.getResponse().setComplete();
        }).block();

        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.HEADER_CORRELATION_ID))
                .isEqualTo(correlationId);

        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.HEADER_CORRELATION_ID))
                .isEqualTo(correlationId);
    }
}
