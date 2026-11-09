package com.ticketya.reservas.controller;

import com.ticketya.reservas.dto.InventarioRequest;
import com.ticketya.reservas.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints internos que invoca ms-eventos (tag Internos). No pasan por el gateway. */
@RestController
@RequestMapping("/internal")
public class InternoInventarioController {

    private final InventarioService inventarioService;

    public InternoInventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @PostMapping("/inventarios")
    public ResponseEntity<Void> crearInventario(@Valid @RequestBody InventarioRequest request) {
        inventarioService.crearInventario(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/eventos/{eventoId}/cerrar")
    public ResponseEntity<Void> cerrar(@PathVariable Long eventoId) {
        inventarioService.cerrar(eventoId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/eventos/{eventoId}/abrir")
    public ResponseEntity<Void> abrir(@PathVariable Long eventoId) {
        inventarioService.abrir(eventoId);
        return ResponseEntity.ok().build();
    }
}
