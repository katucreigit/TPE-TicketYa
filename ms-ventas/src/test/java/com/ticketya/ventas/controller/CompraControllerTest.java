package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.CompraResponse;
import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.MedioPagoResponse;
import com.ticketya.ventas.dto.PageCompraResponse;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.TipoMedioPago;
import com.ticketya.ventas.service.CompraService;
import com.ticketya.ventas.service.CompraService.ResultadoCompra;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompraController.class)
@Import(GlobalExceptionHandler.class)
class CompraControllerTest {

    private static final String BODY_VALIDO = """
            {
              "reservaId": "5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11",
              "medioPago": {"tipo": "TARJETA_CREDITO", "token": "tok_ok", "ultimos4": "4242"}
            }""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CompraService compraService;

    @Test
    void postCompraNuevaResponde201ConLaCompraYNoExponeElToken() throws Exception {
        UUID compraId = UUID.fromString("c0a8f7b2-1e44-4d0b-8a35-55e0b7d1f9aa");
        UUID entradaId = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
        CompraResponse respuesta = new CompraResponse(
                compraId,
                EstadoCompra.APROBADA,
                new BigDecimal("45000.00"),
                new MedioPagoResponse(TipoMedioPago.TARJETA_CREDITO, "4242"),
                List.of(new EntradaResponse(entradaId, 42L, 11L, 3, 10,
                        new BigDecimal("45000.00"), "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64", EstadoEntrada.EMITIDA))
        );
        when(compraService.crear(eq("cli-100"), eq("k-100"), any(CompraRequest.class)))
                .thenReturn(new ResultadoCompra(respuesta, false));

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compraId").value(compraId.toString()))
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.total").value(45000.00))
                .andExpect(jsonPath("$.medioPago.tipo").value("TARJETA_CREDITO"))
                .andExpect(jsonPath("$.medioPago.ultimos4").value("4242"))
                .andExpect(jsonPath("$.medioPago.token").doesNotExist())
                .andExpect(jsonPath("$.entradas[0].codigoQr").value("9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64"));
    }

    @Test
    void postCompraRepetidaPorIdempotenciaResponde200() throws Exception {
        UUID compraId = UUID.fromString("c0a8f7b2-1e44-4d0b-8a35-55e0b7d1f9aa");
        CompraResponse respuesta = new CompraResponse(
                compraId,
                EstadoCompra.APROBADA,
                new BigDecimal("45000.00"),
                new MedioPagoResponse(TipoMedioPago.TARJETA_CREDITO, "4242"),
                List.of()
        );
        when(compraService.crear(eq("cli-100"), eq("k-100"), any(CompraRequest.class)))
                .thenReturn(new ResultadoCompra(respuesta, true));

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compraId").value(compraId.toString()));
    }

    @Test
    void sinIdempotencyKeyOConClaveEnBlancoResponde400IdempotencyKeyRequerida() throws Exception {
        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUERIDA"));

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUERIDA"));

        verifyNoInteractions(compraService);
    }

    @Test
    void sinHeadersDeIdentidadResponde400YConRolDistintoDeClienteResponde403() throws Exception {
        mvc.perform(post("/compras")
                        .header("Idempotency-Key", "k-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "ORGANIZADOR")
                        .header("Idempotency-Key", "k-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        verifyNoInteractions(compraService);
    }

    @Test
    void cuerpoInvalidoResponde400Validacion() throws Exception {
        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId": "5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId": "5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11",
                                 "medioPago": {"tipo": "TARJETA_CREDITO", "token": ""}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        verifyNoInteractions(compraService);
    }

    @Test
    void listarComprasPropiasResponde200ConPaginaDeCompras() throws Exception {
        UUID compraId = UUID.fromString("c0a8f7b2-1e44-4d0b-8a35-55e0b7d1f9aa");
        CompraResponse compra = new CompraResponse(
                compraId, EstadoCompra.APROBADA, new BigDecimal("90000.00"),
                new MedioPagoResponse(TipoMedioPago.TARJETA_CREDITO, "4242"), List.of()
        );
        when(compraService.listarDelCliente("cli-100", 0, 20))
                .thenReturn(new PageCompraResponse(List.of(compra), 0, 20, 1L, 1));

        mvc.perform(get("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].compraId").value(compraId.toString()))
                .andExpect(jsonPath("$.content[0].medioPago.ultimos4").value("4242"));
    }

    @Test
    void obtenerCompraPropiaResponde200Ajena403EInexistente404() throws Exception {
        UUID propiaId = UUID.fromString("c0a8f7b2-1e44-4d0b-8a35-55e0b7d1f9aa");
        UUID ajenaId = UUID.fromString("d0a8f7b2-1e44-4d0b-8a35-55e0b7d1f9bb");
        UUID inexistenteId = UUID.fromString("e0a8f7b2-1e44-4d0b-8a35-55e0b7d1f9cc");
        CompraResponse respuesta = new CompraResponse(
                propiaId, EstadoCompra.APROBADA, new BigDecimal("45000.00"),
                new MedioPagoResponse(TipoMedioPago.TARJETA_CREDITO, "4242"), List.of()
        );
        when(compraService.obtener(propiaId, "cli-100", false)).thenReturn(respuesta);
        when(compraService.obtener(ajenaId, "cli-100", false)).thenThrow(new ForbiddenException());
        when(compraService.obtener(inexistenteId, "cli-100", false)).thenThrow(new NotFoundException("No existe"));

        mvc.perform(get("/compras/" + propiaId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compraId").value(propiaId.toString()));

        mvc.perform(get("/compras/" + ajenaId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        mvc.perform(get("/compras/" + inexistenteId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
    }
}

