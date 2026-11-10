package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.PageEntradaResponse;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.service.EntradaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EntradaController.class)
@Import(GlobalExceptionHandler.class)
class EntradaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EntradaService entradaService;

    @Test
    void listarEntradasPropiasResponde200ConSusCodigosQr() throws Exception {
        UUID entradaId = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
        EntradaResponse entrada = new EntradaResponse(
                entradaId, 42L, 11L, 3, 10,
                new BigDecimal("45000.00"), "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64", EstadoEntrada.EMITIDA
        );
        when(entradaService.listarDelCliente("cli-100", EstadoEntrada.EMITIDA, 42L, 0, 20))
                .thenReturn(new PageEntradaResponse(List.of(entrada), 0, 20, 1L, 1));

        mvc.perform(get("/entradas")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .param("estado", "EMITIDA")
                        .param("eventoId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entradaId").value(entradaId.toString()))
                .andExpect(jsonPath("$.content[0].codigoQr").value("9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64"))
                .andExpect(jsonPath("$.content[0].estado").value("EMITIDA"));
    }

    @Test
    void obtenerEntradaPropiaResponde200Ajena403EInexistente404() throws Exception {
        UUID propiaId = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
        UUID ajenaId = UUID.fromString("e1b2c3d4-0002-4aaa-8bbb-111122223333");
        UUID inexistenteId = UUID.fromString("e1b2c3d4-0003-4aaa-8bbb-111122223333");
        EntradaResponse respuesta = new EntradaResponse(
                propiaId, 42L, 11L, 3, 10,
                new BigDecimal("45000.00"), "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64", EstadoEntrada.EMITIDA
        );
        when(entradaService.obtener(propiaId, "cli-100", false)).thenReturn(respuesta);
        when(entradaService.obtener(ajenaId, "cli-100", false)).thenThrow(new ForbiddenException());
        when(entradaService.obtener(inexistenteId, "cli-100", false)).thenThrow(new NotFoundException("No existe"));

        mvc.perform(get("/entradas/" + propiaId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value(propiaId.toString()))
                .andExpect(jsonPath("$.codigoQr").value("9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64"));

        mvc.perform(get("/entradas/" + ajenaId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        mvc.perform(get("/entradas/" + inexistenteId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
    }
}

