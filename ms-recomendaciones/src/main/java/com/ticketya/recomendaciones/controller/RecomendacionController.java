package com.ticketya.recomendaciones.controller;

import com.ticketya.recomendaciones.dto.RecomendacionesResponse;
import com.ticketya.recomendaciones.dto.TambienCompraronResponse;
import com.ticketya.recomendaciones.service.RecomendacionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recomendaciones (tag Recomendaciones). {@code GET /recomendaciones} es personal (solo CLIENTE);
 * "tambien compraron" acepta cualquier rol autenticado (design.md D7). {@code limite} se valida con la validacion de
 * parametros de Spring MVC (HandlerMethodValidationException, 400 VALIDACION).
 */
@RestController
@RequestMapping("/recomendaciones")
public class RecomendacionController {

    private final RecomendacionService service;

    public RecomendacionController(RecomendacionService service) {
        this.service = service;
    }

    @GetMapping
    public RecomendacionesResponse recomendar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limite debe ser al menos 1")
            @Max(value = 50, message = "limite debe ser como maximo 50") int limite) {
        return service.recomendar(Identidad.cliente(userId, rol), limite);
    }

    @GetMapping("/eventos/{eventoId}/tambien-compraron")
    public TambienCompraronResponse tambienCompraron(
            @PathVariable Long eventoId,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limite debe ser al menos 1")
            @Max(value = 50, message = "limite debe ser como maximo 50") int limite) {
        Identidad.autenticado(userId, rol);
        return service.tambienCompraron(eventoId, limite);
    }
}
