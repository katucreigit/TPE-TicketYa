package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.CancelacionEventoResponse;
import com.ticketya.ventas.service.CancelacionEventoService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno ms-eventos → ms-ventas (operationId internoCancelacionEvento). No pasa por el gateway, por eso
 * no exige los headers de identidad.
 */
@RestController
@RequestMapping("/internal/eventos")
public class CancelacionEventoController {

    private final CancelacionEventoService cancelacionEventoService;

    public CancelacionEventoController(CancelacionEventoService cancelacionEventoService) {
        this.cancelacionEventoService = cancelacionEventoService;
    }

    @PostMapping("/{eventoId}/cancelacion")
    public CancelacionEventoResponse cancelar(@PathVariable Long eventoId) {
        return cancelacionEventoService.procesar(eventoId);
    }
}
