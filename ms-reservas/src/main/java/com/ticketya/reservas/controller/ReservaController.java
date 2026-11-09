package com.ticketya.reservas.controller;

import com.ticketya.reservas.dto.ReservaRequest;
import com.ticketya.reservas.dto.ReservaResponse;
import com.ticketya.reservas.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints publicos de reservas (tag Reservas). Solo rol CLIENTE. */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @Valid @RequestBody ReservaRequest request) {
        String clienteId = Identidad.cliente(userId, rol);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(clienteId, request));
    }

    @GetMapping("/{id}")
    public ReservaResponse obtener(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        return reservaService.obtener(Identidad.cliente(userId, rol), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        reservaService.cancelar(Identidad.cliente(userId, rol), id);
        return ResponseEntity.noContent().build();
    }
}
