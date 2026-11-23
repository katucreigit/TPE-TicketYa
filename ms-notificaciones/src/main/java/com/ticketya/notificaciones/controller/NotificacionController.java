package com.ticketya.notificaciones.controller;

import com.ticketya.notificaciones.dto.NotificacionResponse;
import com.ticketya.notificaciones.dto.PageNotificacionResponse;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import com.ticketya.notificaciones.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta administrativa de notificaciones (solo ADMIN). */
@RestController
@RequestMapping("/notificaciones")
@Tag(name = "Notificaciones")
public class NotificacionController {

    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar notificaciones con filtros y paginacion (ADMIN)")
    public PageNotificacionResponse listar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) TipoNotificacion tipo,
            @RequestParam(required = false) EstadoNotificacion estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Identidad.admin(userId, rol);
        Identidad.validarPaginacion(page, size);
        return PageNotificacionResponse.from(
                service.buscar(usuarioId, tipo, estado, page, size).map(NotificacionResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver el detalle de una notificacion (ADMIN)")
    public NotificacionResponse obtener(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @PathVariable String id) {
        Identidad.admin(userId, rol);
        return NotificacionResponse.from(service.obtener(id));
    }
}
