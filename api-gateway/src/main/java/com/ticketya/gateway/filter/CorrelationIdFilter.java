package com.ticketya.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(HEADER_CORRELATION_ID);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String idFinal = correlationId;

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders()
                    .set(HEADER_CORRELATION_ID, idFinal);
            return Mono.empty();
        });

        ServerWebExchange exchangeMutado = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(HEADER_CORRELATION_ID);
                    headers.add(HEADER_CORRELATION_ID, idFinal);
                }))
                .build();

        return chain.filter(exchangeMutado);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
