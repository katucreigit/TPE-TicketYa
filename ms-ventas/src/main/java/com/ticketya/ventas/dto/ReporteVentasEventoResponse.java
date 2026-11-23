package com.ticketya.ventas.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de {@code GET /internal/eventos/{eventoId}/reporte-ventas} con totales del evento y desglose por sector.
 */
public record ReporteVentasEventoResponse(
        Long eventoId,
        long entradasVendidas,
        long entradasDevueltas,
        long entradasReintegroPendiente,
        long entradasDevolucionSolicitada,
        BigDecimal recaudacionNeta,
        List<ReporteSectorVentasResponse> sectores
) {
    public ReporteVentasEventoResponse {
        recaudacionNeta = recaudacionNeta != null ? recaudacionNeta : BigDecimal.ZERO;
        sectores = sectores != null ? List.copyOf(sectores) : List.of();
    }
}
