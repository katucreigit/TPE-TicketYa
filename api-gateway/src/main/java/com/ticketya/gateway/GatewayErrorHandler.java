

package com.ticketya.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class GatewayErrorHandler implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(ex -> {
                    if (esErrorDeConexion(ex)) {
                        return responderServicioNoDisponible(exchange);
                    }

                    return Mono.error(ex);
                });
    }

    private boolean esErrorDeConexion(Throwable ex) {
        Throwable actual = ex;

        while (actual != null) {
            if (actual instanceof ConnectException
                    || actual instanceof UnknownHostException
                    || actual instanceof SocketTimeoutException
                    || actual instanceof TimeoutException) {
                return true;
            }

            String nombre = actual.getClass().getSimpleName();

            if (nombre.equals("ConnectTimeoutException")
                    || nombre.equals("PrematureCloseException")) {
                return true;
            }

            actual = actual.getCause();
        }

        return false;
    }

    private Mono<Void> responderServicioNoDisponible(ServerWebExchange exchange) {
        var response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException(
                    "No se puede modificar una respuesta ya enviada"
            ));
        }

        var error = new ErrorResponse(
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                503,
                "Service Unavailable",
                "SERVICIO_NO_DISPONIBLE",
                "El servicio destino no está disponible",
                exchange.getRequest().getURI().getPath(),
                List.of()
        );

        try {
            byte[] body = objectMapper.writeValueAsBytes(error);

            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(body)
            ));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String code,
            String message,
            String path,
            List<String> details
    ) {
    }
}
