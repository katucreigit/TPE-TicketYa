
package com.ticketya.eventos.controller;

import com.ticketya.eventos.dto.LugarRequest;
import com.ticketya.eventos.dto.LugarResponse;
import com.ticketya.eventos.exception.GlobalExceptionHandler;
import com.ticketya.eventos.service.LugarService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LugarController.class)
@Import(GlobalExceptionHandler.class)
class LugarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LugarService lugarService;

    @Test
    void crearLugarCorrectamenteDevuelve201() throws Exception {

        LugarResponse respuesta = new LugarResponse(
                1L,
                "Estadio",
                "Calle 123",
                "Tandil",
                new BigDecimal("-37.32"),
                new BigDecimal("-59.13"),
                List.of()
        );

        when(lugarService.crear(any(LugarRequest.class)))
                .thenReturn(respuesta);

        mockMvc.perform(post("/lugares")
                .header("X-User-Id", 1)
                .header("X-User-Rol", "ORGANIZADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "nombre": "Estadio",
                      "direccion": "Calle 123",
                      "ciudad": "Tandil",
                      "latitud": -37.32,
                      "longitud": -59.13,
                      "sectores": []
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Estadio"));

        verify(lugarService).crear(any(LugarRequest.class));
    }

    
@Test
void crearLugarConRolIncorrectoDevuelve403() throws Exception {

    mockMvc.perform(post("/lugares")
            .header("X-User-Id", 1)
            .header("X-User-Rol", "CLIENTE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "nombre": "Estadio",
                  "direccion": "Calle 123",
                  "ciudad": "Tandil",
                  "latitud": -37.32,
                  "longitud": -59.13,
                  "sectores": []
                }
                """))
            .andExpect(status().isForbidden());

    verifyNoInteractions(lugarService);
}


    @Test
    void crearLugarSinHeaderDevuelve400() throws Exception {

        mockMvc.perform(post("/lugares")
                .header("X-User-Rol", "ORGANIZADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lugarService);
    }
}

