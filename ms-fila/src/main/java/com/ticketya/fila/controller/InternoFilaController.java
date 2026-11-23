package com.ticketya.fila.controller;

import com.ticketya.fila.dto.FilaEstadoInterno;
import com.ticketya.fila.service.FilaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta interna (tag Internos) que hace ms-reservas antes de crear una reserva. No pasa por el gateway y no
 * controla identidad, como los demas /internal/**.
 */
@RestController
public class InternoFilaController {

    private final FilaService service;

    public InternoFilaController(FilaService service) {
        this.service = service;
    }

    @GetMapping("/internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado")
    public FilaEstadoInterno estado(@PathVariable Long eventoId, @PathVariable String clienteId) {
        return service.estadoInterno(eventoId, clienteId);
    }
}
