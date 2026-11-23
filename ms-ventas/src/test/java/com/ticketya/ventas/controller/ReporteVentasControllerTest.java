package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.ReporteSectorVentasResponse;
import com.ticketya.ventas.dto.ReporteVentasEventoResponse;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.service.ReporteVentasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReporteVentasController.class)
@Import(GlobalExceptionHandler.class)
class ReporteVentasControllerTest {

    private static final Long EVENTO_ID = 42L;
    private static final Long SECTOR_11 = 11L;
    private static final Long SECTOR_12 = 12L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReporteVentasService reporteVentasService;

    @Test
    void reporteResponde200ConDesglosePorSectorSinHeadersDeIdentidad() throws Exception {
        ReporteVentasEventoResponse respuesta = new ReporteVentasEventoResponse(
                EVENTO_ID,
                11L,
                3L,
                1L,
                1L,
                new BigDecimal("236002.50"),
                List.of(
                        new ReporteSectorVentasResponse(SECTOR_11, 7L, 2L, 1L, 1L, new BigDecimal("180002.00")),
                        new ReporteSectorVentasResponse(SECTOR_12, 4L, 1L, 0L, 0L, new BigDecimal("56000.50"))
                )
        );
        when(reporteVentasService.obtenerPorEvento(EVENTO_ID)).thenReturn(respuesta);

        mvc.perform(get("/internal/eventos/42/reporte-ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(42))
                .andExpect(jsonPath("$.entradasVendidas").value(11))
                .andExpect(jsonPath("$.entradasDevueltas").value(3))
                .andExpect(jsonPath("$.entradasReintegroPendiente").value(1))
                .andExpect(jsonPath("$.entradasDevolucionSolicitada").value(1))
                .andExpect(jsonPath("$.recaudacionNeta").value(236002.5))
                .andExpect(jsonPath("$.sectores", hasSize(2)))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(11))
                .andExpect(jsonPath("$.sectores[0].entradasVendidas").value(7))
                .andExpect(jsonPath("$.sectores[0].recaudacionNeta").value(180002.0))
                .andExpect(jsonPath("$.sectores[1].sectorId").value(12))
                .andExpect(jsonPath("$.sectores[1].entradasVendidas").value(4))
                .andExpect(jsonPath("$.sectores[1].recaudacionNeta").value(56000.5));
    }

    @Test
    void eventoSinVentasResponde200ConCerosYSectoresVacios() throws Exception {
        when(reporteVentasService.obtenerPorEvento(999L))
                .thenReturn(new ReporteVentasEventoResponse(999L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, List.of()));

        mvc.perform(get("/internal/eventos/999/reporte-ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(999))
                .andExpect(jsonPath("$.entradasVendidas").value(0))
                .andExpect(jsonPath("$.entradasDevueltas").value(0))
                .andExpect(jsonPath("$.entradasReintegroPendiente").value(0))
                .andExpect(jsonPath("$.entradasDevolucionSolicitada").value(0))
                .andExpect(jsonPath("$.recaudacionNeta").value(0))
                .andExpect(jsonPath("$.sectores", hasSize(0)));
    }

    @Test
    void eventoIdNoNumericoResponde400Validacion() throws Exception {
        mvc.perform(get("/internal/eventos/abc/reporte-ventas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        verifyNoInteractions(reporteVentasService);
    }
}
