package com.ticketya.fila.controller;

import com.ticketya.fila.dto.FilaEstadoCliente;
import com.ticketya.fila.service.FilaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingreso a la fila y consulta de la posicion (tag Fila). Solo rol CLIENTE. */
@RestController
@RequestMapping("/fila/eventos/{eventoId}")
public class FilaClienteController {

    private final FilaService service;

    public FilaClienteController(FilaService service) {
        this.service = service;
    }

    @PostMapping("/ingresar")
    public FilaEstadoCliente ingresar(
            @PathVariable Long eventoId,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        return service.ingresar(eventoId, Identidad.cliente(userId, rol));
    }

    @GetMapping("/posicion")
    public FilaEstadoCliente posicion(
            @PathVariable Long eventoId,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        return service.posicion(eventoId, Identidad.cliente(userId, rol));
    }
}
