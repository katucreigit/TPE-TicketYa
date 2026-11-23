package com.ticketya.recomendaciones.controller;

import com.ticketya.recomendaciones.dto.PreferenciasRequest;
import com.ticketya.recomendaciones.dto.PreferenciasResponse;
import com.ticketya.recomendaciones.service.PreferenciasService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preferencias del cliente (tag Preferencias). Solo rol CLIENTE. */
@RestController
@RequestMapping("/preferencias")
public class PreferenciasController {

    private final PreferenciasService service;

    public PreferenciasController(PreferenciasService service) {
        this.service = service;
    }

    @GetMapping
    public PreferenciasResponse obtener(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        return service.obtener(Identidad.cliente(userId, rol));
    }

    @PutMapping
    public PreferenciasResponse guardar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @Valid @RequestBody PreferenciasRequest request) {
        return service.guardar(Identidad.cliente(userId, rol), request);
    }
}
