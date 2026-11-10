package com.ticketya.eventos.controller;

import com.ticketya.eventos.dto.LugarRequest;
import com.ticketya.eventos.dto.LugarResponse;
import com.ticketya.eventos.dto.PageLugarResponse;
import com.ticketya.eventos.dto.SectorRequest;
import com.ticketya.eventos.dto.SectorResponse;
import com.ticketya.eventos.exception.ForbiddenException;
import com.ticketya.eventos.service.LugarService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lugares")
public class LugarController {

    private final LugarService lugarService;

    public LugarController(LugarService lugarService) {
        this.lugarService = lugarService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LugarResponse crear(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol,
            @Valid @RequestBody LugarRequest request) {

        validarRol(rol);
        return lugarService.crear(request);
    }

    @GetMapping
    public PageLugarResponse listar(
            @RequestParam(required = false) String ciudad,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return lugarService.listar(ciudad, page, size);
    }

    @GetMapping("/{id}")
    public LugarResponse obtener(@PathVariable Long id) {
        return lugarService.obtener(id);
    }

    @PostMapping("/{id}/sectores")
    @ResponseStatus(HttpStatus.CREATED)
    public SectorResponse agregarSector(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol,
            @Valid @RequestBody SectorRequest request) {

        validarRol(rol);
        return lugarService.agregarSector(id, request);
    }

    private void validarRol(String rol) {
        if (!"ADMIN".equalsIgnoreCase(rol)
                && !"ORGANIZADOR".equalsIgnoreCase(rol)) {
            throw new ForbiddenException(
                    "Solo ORGANIZADOR o ADMIN pueden realizar esta operación."
            );
        }
    }
}
