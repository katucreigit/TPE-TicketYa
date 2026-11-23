package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.ReporteVentasEventoResponse;
import com.ticketya.ventas.service.ReporteVentasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno ms-eventos -> ms-ventas (operationId internoReporteVentasEvento).
 * No pasa por el gateway, por eso no exige headers de identidad ni JWT.
 */
@RestController
@RequestMapping("/internal/eventos")
public class ReporteVentasController {

    private final ReporteVentasService reporteVentasService;

    public ReporteVentasController(ReporteVentasService reporteVentasService) {
        this.reporteVentasService = reporteVentasService;
    }

    @GetMapping("/{eventoId}/reporte-ventas")
    public ReporteVentasEventoResponse obtenerPorEvento(@PathVariable Long eventoId) {
        return reporteVentasService.obtenerPorEvento(eventoId);
    }
}
