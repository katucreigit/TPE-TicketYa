package com.ticketya.reservas.controller;

import com.ticketya.reservas.dto.DevolucionRequest;
import com.ticketya.reservas.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints internos que invoca ms-ventas (tag Internos). No pasan por el gateway. */
@RestController
@RequestMapping("/internal/reservas/{id}")
public class InternoReservaController {

    private final ReservaService reservaService;

    public InternoReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmar(@PathVariable UUID id) {
        reservaService.confirmar(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/liberar")
    public ResponseEntity<Void> liberar(@PathVariable UUID id) {
        reservaService.liberar(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/devolver")
    public ResponseEntity<Void> devolver(@PathVariable UUID id, @Valid @RequestBody DevolucionRequest request) {
        reservaService.devolver(id, request);
        return ResponseEntity.ok().build();
    }
}
