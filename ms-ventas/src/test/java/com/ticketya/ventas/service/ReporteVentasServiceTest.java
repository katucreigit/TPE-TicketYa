package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.ReporteSectorVentasResponse;
import com.ticketya.ventas.dto.ReporteVentasEventoResponse;
import com.ticketya.ventas.repository.EntradaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReporteVentasServiceTest {

    private static final Long EVENTO_ID = 42L;
    private static final Long SECTOR_11 = 11L;
    private static final Long SECTOR_12 = 12L;

    private EntradaRepository entradaRepository;
    private ReporteVentasService servicio;

    @BeforeEach
    void setUp() {
        entradaRepository = mock(EntradaRepository.class);
        servicio = new ReporteVentasService(entradaRepository);
    }

    @Test
    void consolidaTotalesDeMultiplesSectoresConPrecisionDecimalExacta() {
        ReporteSectorVentasResponse sector11 = new ReporteSectorVentasResponse(
                SECTOR_11, 7L, 2L, 1L, 1L, new BigDecimal("180002.00"));
        ReporteSectorVentasResponse sector12 = new ReporteSectorVentasResponse(
                SECTOR_12, 4L, 1L, 0L, 0L, new BigDecimal("56000.50"));

        when(entradaRepository.resumirVentasPorSector(EVENTO_ID)).thenReturn(List.of(sector11, sector12));

        ReporteVentasEventoResponse reporte = servicio.obtenerPorEvento(EVENTO_ID);

        assertEquals(EVENTO_ID, reporte.eventoId());
        assertEquals(11L, reporte.entradasVendidas());
        assertEquals(3L, reporte.entradasDevueltas());
        assertEquals(1L, reporte.entradasReintegroPendiente());
        assertEquals(1L, reporte.entradasDevolucionSolicitada());
        assertEquals(0, new BigDecimal("236002.50").compareTo(reporte.recaudacionNeta()));
        assertEquals(List.of(sector11, sector12), reporte.sectores());
        verify(entradaRepository).resumirVentasPorSector(EVENTO_ID);
    }

    @Test
    void eventoSinVentasAprobadasDevuelveCerosYListaVaciaSinNulos() {
        when(entradaRepository.resumirVentasPorSector(999L)).thenReturn(List.of());

        ReporteVentasEventoResponse reporte = servicio.obtenerPorEvento(999L);

        assertEquals(999L, reporte.eventoId());
        assertEquals(0L, reporte.entradasVendidas());
        assertEquals(0L, reporte.entradasDevueltas());
        assertEquals(0L, reporte.entradasReintegroPendiente());
        assertEquals(0L, reporte.entradasDevolucionSolicitada());
        assertNotNull(reporte.recaudacionNeta());
        assertEquals(0, BigDecimal.ZERO.compareTo(reporte.recaudacionNeta()));
        assertNotNull(reporte.sectores());
        assertTrue(reporte.sectores().isEmpty());
    }

    @Test
    void sectorConReintegrosTotalesMantieneRecaudacionNetaCero() {
        ReporteSectorVentasResponse sectorReintegrado = new ReporteSectorVentasResponse(
                SECTOR_11, 2L, 2L, 0L, 0L, new BigDecimal("0.00"));

        when(entradaRepository.resumirVentasPorSector(EVENTO_ID)).thenReturn(List.of(sectorReintegrado));

        ReporteVentasEventoResponse reporte = servicio.obtenerPorEvento(EVENTO_ID);

        assertEquals(2L, reporte.entradasVendidas());
        assertEquals(2L, reporte.entradasDevueltas());
        assertNotNull(reporte.recaudacionNeta());
        assertEquals(0, BigDecimal.ZERO.compareTo(reporte.recaudacionNeta()));
        assertEquals(1, reporte.sectores().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(reporte.sectores().get(0).recaudacionNeta()));
    }
}
