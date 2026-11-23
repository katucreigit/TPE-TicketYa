package com.ticketya.ventas.dto;

import java.math.BigDecimal;

/**
 * Métricas agregadas de ventas para un sector dentro de un evento.
 */
public record ReporteSectorVentasResponse(
        Long sectorId,
        long entradasVendidas,
        long entradasDevueltas,
        long entradasReintegroPendiente,
        long entradasDevolucionSolicitada,
        BigDecimal recaudacionNeta
) {
    public ReporteSectorVentasResponse {
        recaudacionNeta = recaudacionNeta != null ? recaudacionNeta : BigDecimal.ZERO;
    }

    /**
     * Constructor auxiliar para proyección JPQL {@code SELECT new} en Hibernate 6
     * ({@code COUNT}/{@code SUM} devuelven {@link Long}).
     */
    public ReporteSectorVentasResponse(
            Long sectorId,
            Long entradasVendidas,
            Long entradasDevueltas,
            Long entradasReintegroPendiente,
            Long entradasDevolucionSolicitada,
            BigDecimal recaudacionNeta
    ) {
        this(
                sectorId,
                entradasVendidas != null ? entradasVendidas : 0L,
                entradasDevueltas != null ? entradasDevueltas : 0L,
                entradasReintegroPendiente != null ? entradasReintegroPendiente : 0L,
                entradasDevolucionSolicitada != null ? entradasDevolucionSolicitada : 0L,
                recaudacionNeta != null ? recaudacionNeta : BigDecimal.ZERO
        );
    }
}
