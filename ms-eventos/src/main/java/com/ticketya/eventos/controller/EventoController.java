package com.ticketya.eventos.controller;

import com.ticketya.eventos.dto.EventoRequest;
import com.ticketya.eventos.dto.PageEventoResumenResponse;
import com.ticketya.eventos.model.enums.TipoEvento;
import java.time.LocalDate;
import com.ticketya.eventos.dto.MotivoRequest;
import com.ticketya.eventos.dto.EventoResponse;
import com.ticketya.eventos.dto.PrecioSectorRequest;
import com.ticketya.eventos.dto.SectorEventoResponse;
import com.ticketya.eventos.exception.ForbiddenException;
import com.ticketya.eventos.service.EventoService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/eventos")
public class EventoController {
    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse crear(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol,
            @Valid @RequestBody EventoRequest request) {

        if (!"ORGANIZADOR".equalsIgnoreCase(rol)) {
            throw new ForbiddenException(
                    "Solo los ORGANIZADORES pueden crear eventos."
            );
        }

        return eventoService.crear(userId, request);
    }

    @PutMapping("/{id}")
    public EventoResponse actualizar(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol,
            @Valid @RequestBody EventoRequest request) {

        if (!"ORGANIZADOR".equalsIgnoreCase(rol)) {
            throw new ForbiddenException(
                    "Solo los ORGANIZADORES pueden editar eventos."
            );
        }

        return eventoService.actualizar(id, userId, request);
    }

    @PutMapping("/{id}/precios")
    public List<SectorEventoResponse> configurarPrecios(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol,
            @Valid @RequestBody List<@Valid PrecioSectorRequest> precios) {

        if (!"ORGANIZADOR".equalsIgnoreCase(rol)) {
            throw new ForbiddenException(
                    "Solo los ORGANIZADORES pueden configurar precios."
            );
        }

        return eventoService.configurarPrecios(id, userId, precios);
    }
    @PostMapping("/{id}/publicar")
    public EventoResponse publicar(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol) {

        if (!"ORGANIZADOR".equalsIgnoreCase(rol)) {
            throw new ForbiddenException(
                    "Solo los ORGANIZADORES pueden publicar eventos."
            );
        }

        return eventoService.publicar(id, userId);
    }

@PostMapping("/{id}/suspender")
public EventoResponse suspender(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-User-Rol") String rol) {

    return eventoService.suspender(id, rol);
}
@PostMapping("/{id}/reanudar")
public EventoResponse reanudar(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-User-Rol") String rol) {

    return eventoService.reanudar(id, rol);
}
    @GetMapping
    public PageEventoResumenResponse buscarEventos(
            @RequestParam(required = false) TipoEvento tipo,
            @RequestParam(required = false) Long organizadorId,
            @RequestParam(required = false) String participante,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "fecha") String orden,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return eventoService.buscarEventos(
                tipo, organizadorId, participante, ciudad,
                fechaDesde, fechaHasta, q, orden, dir, page, size
        );
    }

    @GetMapping("/{id}")
    public EventoResponse obtenerEvento(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-User-Rol", required = false) String rol) {

        return eventoService.obtenerEvento(id, usuarioId, rol);
    }

    @GetMapping("/{id}/sectores")
    public List<SectorEventoResponse> obtenerSectores(@PathVariable Long id) {
        return eventoService.obtenerSectores(id);
    }

    @PostMapping("/{id}/cancelar")
    public EventoResponse cancelar(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Rol") String rol,
            @Valid @RequestBody MotivoRequest request) {

        return eventoService.cancelar(id, rol, request.motivo());
    }

}
