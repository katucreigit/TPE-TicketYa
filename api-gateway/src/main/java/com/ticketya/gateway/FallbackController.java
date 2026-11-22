package com.ticketya.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/{servicio}")
    public ResponseEntity<ErrorResponse> servicioNoDisponible() {
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
