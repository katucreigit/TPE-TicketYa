package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.DevolucionRequest;
import com.ticketya.ventas.dto.DevolucionResponse;
import com.ticketya.ventas.dto.PageDevolucionResponse;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.model.enums.EstadoDevolucion;
import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.service.DevolucionService;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevolucionController.class)
@Import(GlobalExceptionHandler.class)
class DevolucionControllerTest {

    private static final UUID DEVOLUCION_ID = UUID.fromString("d4e5f6a7-0001-4bbb-9ccc-444455556666");
    private static final UUID ENTRADA_ID = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
    private static final String BODY = """
            {"entradaId":"e1b2c3d4-0001-4aaa-8bbb-111122223333","motivo":"No puedo asistir"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DevolucionService devolucionService;

    private static DevolucionResponse respuesta(EstadoDevolucion estado, DevolucionResponse.ReintegroResponse reintegro) {
        return new DevolucionResponse(DEVOLUCION_ID, ENTRADA_ID, estado, new BigDecimal("45000.00"),
                "No puedo asistir", null, LocalDateTime.of(2026, 11, 7, 17, 30), reintegro);
    }

    @Test
    void solicitarComoClienteResponde201() throws Exception {
        when(devolucionService.solicitar("cli-100", new DevolucionRequest(ENTRADA_ID, "No puedo asistir")))
                .thenReturn(respuesta(EstadoDevolucion.SOLICITADA, null));

        mvc.perform(post("/devoluciones")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devolucionId").value(DEVOLUCION_ID.toString()))
                .andExpect(jsonPath("$.entradaId").value(ENTRADA_ID.toString()))
                .andExpect(jsonPath("$.estado").value("SOLICITADA"))
                .andExpect(jsonPath("$.monto").value(45000.00))
                .andExpect(jsonPath("$.solicitadaEn").value("2026-11-07T17:30:00"));
    }

    @Test
    void solicitarSinEntradaIdResponde400() throws Exception {
        mvc.perform(post("/devoluciones")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    void solicitarComoAdminResponde403() throws Exception {
        mvc.perform(post("/devoluciones")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Rol", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(devolucionService);
    }

    @Test
    void solicitarFueraDePlazoResponde422YEnCursoResponde409() throws Exception {
        when(devolucionService.solicitar(any(), any()))
                .thenThrow(new BusinessRuleException("FUERA_DE_PLAZO", "Fuera de plazo"))
                .thenThrow(new ConflictException("ENTRADA_NO_DEVOLVIBLE", "En curso"));

        mvc.perform(post("/devoluciones")
                        .header("X-User-Id", "cli-100").header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FUERA_DE_PLAZO"));

        mvc.perform(post("/devoluciones")
                        .header("X-User-Id", "cli-100").header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTRADA_NO_DEVOLVIBLE"));
    }

    @Test
    void listarSoloAdminYFiltraPorEstado() throws Exception {
        when(devolucionService.listar(EstadoDevolucion.SOLICITADA, 0, 20))
                .thenReturn(new PageDevolucionResponse(
                        List.of(respuesta(EstadoDevolucion.SOLICITADA, null)), 0, 20, 1L, 1));

        mvc.perform(get("/devoluciones")
                        .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN")
                        .param("estado", "SOLICITADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].estado").value("SOLICITADA"));

        mvc.perform(get("/devoluciones")
                        .header("X-User-Id", "cli-100").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerComoClienteDueno() throws Exception {
        when(devolucionService.obtener(DEVOLUCION_ID, "cli-100", false))
                .thenReturn(respuesta(EstadoDevolucion.SOLICITADA, null));

        mvc.perform(get("/devoluciones/" + DEVOLUCION_ID)
                        .header("X-User-Id", "cli-100").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devolucionId").value(DEVOLUCION_ID.toString()));
    }

    @Test
    void aprobarComoAdminResponde200ConReintegroCompletado() throws Exception {
        when(devolucionService.aprobar(DEVOLUCION_ID, "admin-1")).thenReturn(respuesta(
                EstadoDevolucion.REINTEGRADA,
                new DevolucionResponse.ReintegroResponse(new BigDecimal("45000.00"), EstadoReintegro.COMPLETADO)));

        mvc.perform(post("/devoluciones/" + DEVOLUCION_ID + "/aprobar")
                        .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REINTEGRADA"))
                .andExpect(jsonPath("$.reintegro.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.reintegro.monto").value(45000.00));
    }

    @Test
    void aprobarComoClienteResponde403YFallaRemotaResponde502() throws Exception {
        mvc.perform(post("/devoluciones/" + DEVOLUCION_ID + "/aprobar")
                        .header("X-User-Id", "cli-100").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden());

        when(devolucionService.aprobar(DEVOLUCION_ID, "admin-1"))
                .thenThrow(new BadGatewayException("Pasarela caida"));
        mvc.perform(post("/devoluciones/" + DEVOLUCION_ID + "/aprobar")
                        .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void rechazarRequiereMotivoYRolAdmin() throws Exception {
        when(devolucionService.rechazar(DEVOLUCION_ID, "admin-1", "La entrada ya fue transferida"))
                .thenReturn(new DevolucionResponse(DEVOLUCION_ID, ENTRADA_ID, EstadoDevolucion.RECHAZADA,
                        new BigDecimal("45000.00"), "No puedo asistir", "La entrada ya fue transferida",
                        LocalDateTime.of(2026, 11, 7, 17, 30), null));

        mvc.perform(post("/devoluciones/" + DEVOLUCION_ID + "/rechazar")
                        .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"La entrada ya fue transferida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("La entrada ya fue transferida"));

        mvc.perform(post("/devoluciones/" + DEVOLUCION_ID + "/rechazar")
                        .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/devoluciones/" + DEVOLUCION_ID + "/rechazar")
                        .header("X-User-Id", "cli-100").header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
