package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.PageEntradaResponse;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.service.EntradaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EntradaController.class)
@Import(GlobalExceptionHandler.class)
class EntradaControllerTest {

    private static final String QR = "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EntradaService entradaService;

    @Test
    void listarEntradasPropiasResponde200ConSusCodigosQr() throws Exception {
        UUID entradaId = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
        EntradaResponse entrada = new EntradaResponse(
                entradaId, 42L, 11L, 3, 10,
                new BigDecimal("45000.00"), QR, EstadoEntrada.EMITIDA
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
                .andExpect(jsonPath("$.content[0].codigoQr").value(QR))
                .andExpect(jsonPath("$.content[0].estado").value("EMITIDA"))
                .andExpect(jsonPath("$.content[0].usadaEn").doesNotExist())
                .andExpect(jsonPath("$.content[0].validadaPor").doesNotExist());
    }

    @Test
    void obtenerEntradaPropiaResponde200Ajena403EInexistente404() throws Exception {
        UUID propiaId = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
        UUID ajenaId = UUID.fromString("e1b2c3d4-0002-4aaa-8bbb-111122223333");
        UUID inexistenteId = UUID.fromString("e1b2c3d4-0003-4aaa-8bbb-111122223333");
        EntradaResponse respuesta = new EntradaResponse(
                propiaId, 42L, 11L, 3, 10,
                new BigDecimal("45000.00"), QR, EstadoEntrada.EMITIDA
        );
        when(entradaService.obtener(propiaId, "cli-100", false)).thenReturn(respuesta);
        when(entradaService.obtener(ajenaId, "cli-100", false)).thenThrow(new ForbiddenException());
        when(entradaService.obtener(inexistenteId, "cli-100", false)).thenThrow(new NotFoundException("No existe"));

        mvc.perform(get("/entradas/" + propiaId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value(propiaId.toString()))
                .andExpect(jsonPath("$.codigoQr").value(QR));

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

    @Test
    void validarQrValidoResponde200ConUsadaEnYValidadaPorDeLaIdentidad() throws Exception {
        UUID entradaId = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
        LocalDateTime usadaEn = LocalDateTime.of(2026, 11, 23, 20, 5, 0);
        EntradaResponse respuesta = new EntradaResponse(
                entradaId, 42L, 11L, 3, 10,
                new BigDecimal("45000.00"), QR, EstadoEntrada.USADA, usadaEn, "acc-7"
        );
        when(entradaService.validar(QR, 42L, "acc-7")).thenReturn(respuesta);

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "%s", "eventoId": 42, "validadaPor": "suplantado"}""".formatted(QR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value(entradaId.toString()))
                .andExpect(jsonPath("$.estado").value("USADA"))
                .andExpect(jsonPath("$.usadaEn").value("2026-11-23T20:05:00"))
                .andExpect(jsonPath("$.validadaPor").value("acc-7"));

        verify(entradaService).validar(QR, 42L, "acc-7");
    }

    @Test
    void validarSinHeadersDeIdentidadResponde400Validacion() throws Exception {
        String body = """
                {"codigoQr": "%s", "eventoId": 42}""".formatted(QR);

        mvc.perform(post("/entradas/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        verifyNoInteractions(entradaService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ADMIN", "ORGANIZADOR", "ACCESO"})
    void validarConRolDistintoDePersonalAccesoResponde403AccesoDenegado(String rol) throws Exception {
        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "usr-1")
                        .header("X-User-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "%s", "eventoId": 42}""".formatted(QR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        verifyNoInteractions(entradaService);
    }

    @Test
    void validarConCuerpoAusenteMalFormadoOInvalidoResponde400Validacion() throws Exception {
        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{mal formado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "   ", "eventoId": 42}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "%s"}""".formatted(QR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        verifyNoInteractions(entradaService);
    }

    @Test
    void validarTraduceLosRechazosDeNegocioConSusCodigos() throws Exception {
        when(entradaService.validar("no-es-un-uuid", 42L, "acc-7"))
                .thenThrow(new NotFoundException("QR_INEXISTENTE", "No existe el QR"));
        when(entradaService.validar(QR, 99L, "acc-7"))
                .thenThrow(new BusinessRuleException("EVENTO_INCORRECTO", "Otro evento"));
        when(entradaService.validar(QR, 42L, "acc-7"))
                .thenThrow(new ConflictException("ENTRADA_USADA", "Ya usada en 2026-11-23T19:00"))
                .thenThrow(new ConflictException("ENTRADA_NO_VALIDA", "Estado actual: DEVOLUCION_SOLICITADA"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "no-es-un-uuid", "eventoId": 42}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_INEXISTENTE"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "%s", "eventoId": 99}""".formatted(QR)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENTO_INCORRECTO"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "%s", "eventoId": 42}""".formatted(QR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTRADA_USADA"));

        mvc.perform(post("/entradas/validar")
                        .header("X-User-Id", "acc-7")
                        .header("X-User-Rol", "PERSONAL_ACCESO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoQr": "%s", "eventoId": 42}""".formatted(QR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTRADA_NO_VALIDA"));
    }
}
