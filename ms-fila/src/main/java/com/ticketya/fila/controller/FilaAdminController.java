package com.ticketya.fila.controller;

import com.ticketya.fila.dto.ActivarFilaRequest;
import com.ticketya.fila.dto.FilaConfiguracion;
import com.ticketya.fila.service.FilaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activacion y desactivacion de la fila de un evento (tag Fila). Roles ADMIN y ORGANIZADOR. Se exponen por el
 * gateway en /api/fila/** y no en /internal, para que el organizador pueda usarlos (design.md D8).
 */
@RestController
@RequestMapping("/fila/eventos/{eventoId}")
public class FilaAdminController {

    private final FilaService service;

    public FilaAdminController(FilaService service) {
        this.service = service;
    }

    @PostMapping("/activar")
    public FilaConfiguracion activar(
            @PathVariable Long eventoId,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @Valid @RequestBody(required = false) ActivarFilaRequest request) {
        Identidad.administrador(userId, rol);
        return service.activar(eventoId, request);
    }

    @PostMapping("/desactivar")
    public ResponseEntity<Void> desactivar(
            @PathVariable Long eventoId,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        Identidad.administrador(userId, rol);
        service.desactivar(eventoId);
        return ResponseEntity.noContent().build();
    }
}
