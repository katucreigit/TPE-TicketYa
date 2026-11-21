package com.ticketya.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long inicio = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    String metodo = exchange.getRequest().getMethod().name();
                    String endpoint = exchange.getRequest().getURI().getPath();

                    String usuario = exchange.getRequest()
                            .getHeaders()
                            .getFirst("X-User-Id");

                    String correlationId = exchange.getRequest()
                            .getHeaders()
                            .getFirst(CorrelationIdFilter.HEADER_CORRELATION_ID);

                    HttpStatusCode status = exchange.getResponse().getStatusCode();

                    long duracion = System.currentTimeMillis() - inicio;

                    log.info(
                            "HTTP_REQUEST method={} endpoint={} user={} status={} durationMs={} correlationId={}",
                            metodo,
                            endpoint,
                            usuario != null ? usuario : "ANONIMO",
                            status != null ? status.value() : "SIN_RESPUESTA",
                            duracion,
                            correlationId
                    );
                });
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
