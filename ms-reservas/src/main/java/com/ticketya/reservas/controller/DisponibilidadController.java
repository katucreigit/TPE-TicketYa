package com.ticketya.reservas.controller;

import com.ticketya.reservas.dto.AsientoEstadoResponse;
import com.ticketya.reservas.dto.DisponibilidadResponse;
import com.ticketya.reservas.model.enums.EstadoAsiento;
import com.ticketya.reservas.service.InventarioService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints publicos de disponibilidad (tag Disponibilidad). No requieren identidad ni se cachean. */
@RestController
@RequestMapping("/eventos/{eventoId}")
public class DisponibilidadController {

    private final InventarioService inventarioService;

    public DisponibilidadController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> disponibilidad(@PathVariable Long eventoId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(inventarioService.disponibilidad(eventoId));
    }

    @GetMapping("/sectores/{sectorId}/asientos")
    public List<AsientoEstadoResponse> asientos(@PathVariable Long eventoId,
                                                @PathVariable Long sectorId,
                                                @RequestParam(required = false) EstadoAsiento estado) {
        return inventarioService.asientos(eventoId, sectorId, estado);
    }
}
