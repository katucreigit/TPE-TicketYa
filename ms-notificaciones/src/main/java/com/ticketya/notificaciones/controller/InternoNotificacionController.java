package com.ticketya.notificaciones.controller;

import com.ticketya.notificaciones.dto.NotificacionAceptadaResponse;
import com.ticketya.notificaciones.dto.NotificacionRequest;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoint interno: solo lo invocan otros microservicios, no se enruta por el gateway. */
@RestController
@RequestMapping("/internal/notificaciones")
@Tag(name = "Internos")
public class InternoNotificacionController {

    static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    static final String MDC_CORRELATION_ID = "correlationId";

    private final NotificacionService service;

    public InternoNotificacionController(NotificacionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "[cualquier ms -> ms-notificaciones] Pedir el envio de una notificacion")
    public ResponseEntity<NotificacionAceptadaResponse> recibir(
            @Valid @RequestBody NotificacionRequest request,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {
        String id = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        MDC.put(MDC_CORRELATION_ID, id);
        try {
            Notificacion n = service.recibir(request, id);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(HEADER_CORRELATION_ID, id)
                    .body(NotificacionAceptadaResponse.from(n));
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
