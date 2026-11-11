package com.ticketya.ventas.controller;

import com.ticketya.ventas.dto.CancelacionEventoResponse;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.service.CancelacionEventoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CancelacionEventoController.class)
@Import(GlobalExceptionHandler.class)
class CancelacionEventoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CancelacionEventoService cancelacionEventoService;

    @Test
    void cancelacionRespondeLosContadoresSinHeadersDeIdentidad() throws Exception {
        when(cancelacionEventoService.procesar(42L))
                .thenReturn(new CancelacionEventoResponse(42L, 318, 317, 1));

        mvc.perform(post("/internal/eventos/42/cancelacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(42))
                .andExpect(jsonPath("$.entradasAfectadas").value(318))
                .andExpect(jsonPath("$.reintegrosCompletados").value(317))
                .andExpect(jsonPath("$.reintegrosPendientes").value(1));
    }

    @Test
    void eventoIdNoNumericoResponde400() throws Exception {
        mvc.perform(post("/internal/eventos/abc/cancelacion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        verifyNoInteractions(cancelacionEventoService);
    }
}
