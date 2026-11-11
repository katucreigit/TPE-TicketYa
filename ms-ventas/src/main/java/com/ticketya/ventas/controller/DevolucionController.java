package com.ticketya.ventas.controller;

import com.ticketya.ventas.controller.Identidad.Solicitante;
import com.ticketya.ventas.dto.DevolucionRequest;
import com.ticketya.ventas.dto.DevolucionResponse;
import com.ticketya.ventas.dto.MotivoRequest;
import com.ticketya.ventas.dto.PageDevolucionResponse;
import com.ticketya.ventas.model.enums.EstadoDevolucion;
import com.ticketya.ventas.service.DevolucionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints de devoluciones (tag Devoluciones de openapi.yaml). */
@RestController
@RequestMapping("/devoluciones")
public class DevolucionController {

    private final DevolucionService devolucionService;

    public DevolucionController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    @PostMapping
    public ResponseEntity<DevolucionResponse> solicitar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @Valid @RequestBody DevolucionRequest request) {
        String clienteId = Identidad.cliente(userId, rol);
        return ResponseEntity.status(HttpStatus.CREATED).body(devolucionService.solicitar(clienteId, request));
    }

    @GetMapping
    public PageDevolucionResponse listar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestParam(required = false) EstadoDevolucion estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Identidad.admin(userId, rol);
        Identidad.validarPaginacion(page, size);
        return devolucionService.listar(estado, page, size);
    }

    @GetMapping("/{id}")
    public DevolucionResponse obtener(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        Solicitante solicitante = Identidad.clienteOAdmin(userId, rol);
        return devolucionService.obtener(id, solicitante.userId(), solicitante.esAdmin());
    }

    @PostMapping("/{id}/aprobar")
    public DevolucionResponse aprobar(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        String adminId = Identidad.admin(userId, rol);
        return devolucionService.aprobar(id, adminId);
    }

    @PostMapping("/{id}/rechazar")
    public DevolucionResponse rechazar(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @Valid @RequestBody MotivoRequest request) {
        String adminId = Identidad.admin(userId, rol);
        return devolucionService.rechazar(id, adminId, request.motivo());
    }
}
