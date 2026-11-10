package com.ticketya.eventos.controller;

import com.ticketya.eventos.dto.DisponibilidadRequest;
import com.ticketya.eventos.service.EventoService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/eventos")
public class EventoInternoController {

    private final EventoService eventoService;

    public EventoInternoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @PutMapping("/{id}/disponibilidad")
    @ResponseStatus(HttpStatus.OK)
    public void actualizarDisponibilidad(
            @PathVariable Long id,
            @Valid @RequestBody DisponibilidadRequest request) {

        eventoService.actualizarDisponibilidad(id, request.agotado());
    }
}
