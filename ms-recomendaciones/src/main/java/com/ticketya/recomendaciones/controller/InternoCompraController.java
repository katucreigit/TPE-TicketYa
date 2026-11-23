package com.ticketya.recomendaciones.controller;

import com.ticketya.recomendaciones.dto.CompraRegistradaRequest;
import com.ticketya.recomendaciones.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de compras confirmadas (tag Internos), llamado por ms-ventas. No pasa por el gateway y no controla
 * identidad, como los demas /internal/**. Responde 204 tanto si la compra es nueva como si ya estaba registrada.
 */
@RestController
public class InternoCompraController {

    private final CompraService service;

    public InternoCompraController(CompraService service) {
        this.service = service;
    }

    @PostMapping("/internal/compras")
    public ResponseEntity<Void> registrar(@Valid @RequestBody CompraRegistradaRequest request) {
        service.registrar(request);
        return ResponseEntity.noContent().build();
    }
}
