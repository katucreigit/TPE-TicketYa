package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.ReporteSectorVentasResponse;
import com.ticketya.ventas.dto.ReporteVentasEventoResponse;
import com.ticketya.ventas.repository.EntradaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consulta agregada de métricas de ventas y recaudación neta por evento.
 */
@Service
public class ReporteVentasService {

    private final EntradaRepository entradaRepository;

    public ReporteVentasService(EntradaRepository entradaRepository) {
        this.entradaRepository = entradaRepository;
    }

    @Transactional(readOnly = true)
    public ReporteVentasEventoResponse obtenerPorEvento(Long eventoId) {
        List<ReporteSectorVentasResponse> sectores = entradaRepository.resumirVentasPorSector(eventoId);
        if (sectores == null || sectores.isEmpty()) {
            return new ReporteVentasEventoResponse(eventoId, 0L, 0L, 0L, 0L, BigDecimal.ZERO, List.of());
        }

        long entradasVendidas = 0L;
        long entradasDevueltas = 0L;
        long entradasReintegroPendiente = 0L;
        long entradasDevolucionSolicitada = 0L;
        BigDecimal recaudacionNeta = BigDecimal.ZERO;

        for (ReporteSectorVentasResponse sector : sectores) {
            entradasVendidas += sector.entradasVendidas();
            entradasDevueltas += sector.entradasDevueltas();
            entradasReintegroPendiente += sector.entradasReintegroPendiente();
            entradasDevolucionSolicitada += sector.entradasDevolucionSolicitada();
            recaudacionNeta = recaudacionNeta.add(sector.recaudacionNeta());
        }

        return new ReporteVentasEventoResponse(
                eventoId,
                entradasVendidas,
                entradasDevueltas,
                entradasReintegroPendiente,
                entradasDevolucionSolicitada,
                recaudacionNeta,
                sectores
        );
    }
}
