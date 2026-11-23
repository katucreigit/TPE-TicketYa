package com.ticketya.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
public class FallbackController {

    private static final Logger log =
            LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping("/fallback/{servicio}")
    public ResponseEntity<ErrorResponse> servicioNoDisponible(
            ServerWebExchange exchange) {

        Throwable causa = exchange.getAttribute(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR
        );

        if (causa != null) {
            log.error("Circuit Breaker activo. Path={}, causa={}",
                    exchange.getRequest().getURI().getPath(),
                    causa.getMessage(),
                    causa);
        } else {
            log.error("Fallback ejecutado sin excepción disponible. Path={}",
                    exchange.getRequest().getURI().getPath());
        }

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                503,
                "Service Unavailable",
                "SERVICIO_NO_DISPONIBLE",
                "El servicio destino no está disponible temporalmente. Intente nuevamente más tarde.",
                "/api",
                List.of()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
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
