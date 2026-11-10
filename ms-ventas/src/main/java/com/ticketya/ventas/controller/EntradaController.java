package com.ticketya.ventas.controller;

import com.ticketya.ventas.controller.Identidad.Solicitante;
import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.PageEntradaResponse;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.service.EntradaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints publicos de entradas (tag Entradas de openapi.yaml). */
@RestController
@RequestMapping("/entradas")
public class EntradaController {

    private final EntradaService entradaService;

    public EntradaController(EntradaService entradaService) {
        this.entradaService = entradaService;
    }

    @GetMapping
    public PageEntradaResponse listar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestParam(required = false) EstadoEntrada estado,
            @RequestParam(required = false) Long eventoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String clienteId = Identidad.cliente(userId, rol);
        Identidad.validarPaginacion(page, size);
        return entradaService.listarDelCliente(clienteId, estado, eventoId, page, size);
    }

    @GetMapping("/{id}")
    public EntradaResponse obtener(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        Solicitante solicitante = Identidad.clienteOAdmin(userId, rol);
        return entradaService.obtener(id, solicitante.userId(), solicitante.esAdmin());
    }
}

