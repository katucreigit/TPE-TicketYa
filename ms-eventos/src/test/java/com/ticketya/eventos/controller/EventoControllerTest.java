package com.ticketya.eventos.controller;

import com.ticketya.eventos.exception.ForbiddenException;
import com.ticketya.eventos.exception.GlobalExceptionHandler;
import com.ticketya.eventos.service.EventoService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.ticketya.eventos.exception.NotFoundException;
import com.ticketya.eventos.dto.EventoRequest;
import com.ticketya.eventos.dto.EventoResponse;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.model.enums.TipoEvento;
import java.time.LocalDateTime;
import java.util.List;
import com.ticketya.eventos.dto.EventoRequest;
import com.ticketya.eventos.exception.BusinessRuleException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(EventoController.class)
@Import(GlobalExceptionHandler.class)
class EventoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventoService eventoService;

    @Test
    void crearEventoConRolIncorrectoDevuelve403() throws Exception {

        mockMvc.perform(post("/eventos")
                .header("X-User-Id", 1)
                .header("X-User-Rol", "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON)
                                  .content("""
      {
        "nombre": "Concierto de prueba",
        "tipo": "RECITAL",
        "lugarId": 1,
        "fechaHora": "2027-12-20T20:00:00"
      }
      """))
                  .andExpect(status().isForbidden());

        verifyNoInteractions(eventoService);
    }

    @Test
    void crearEventoSinHeaderDeUsuarioDevuelve400() throws Exception {

        mockMvc.perform(post("/eventos")
                .header("X-User-Rol", "ORGANIZADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(eventoService);
    }
    @Test
void obtenerEventoInexistenteDevuelve404() throws Exception {

    when(eventoService.obtenerEvento(999L, null, null))
            .thenThrow(new NotFoundException(
                    "EVENTO_NO_ENCONTRADO",
                    "No se encontró el evento."
            ));

    mockMvc.perform(get("/eventos/999"))
            .andExpect(status().isNotFound());

    verify(eventoService).obtenerEvento(999L, null, null);
}
@Test
void publicarEventoEnEstadoIncorrectoDevuelve409() throws Exception {

    when(eventoService.publicar(1L, 1L))
            .thenThrow(new com.ticketya.eventos.exception.ConflictException(
                    "ESTADO_INVALIDO",
                    "El evento no está en estado BORRADOR."
            ));

    mockMvc.perform(post("/eventos/1/publicar")
            .header("X-User-Id", 1)
            .header("X-User-Rol", "ORGANIZADOR"))
            .andExpect(status().isConflict());

    verify(eventoService).publicar(1L, 1L);
}
@Test
void crearEventoCorrectamenteDevuelve201() throws Exception {

    EventoResponse respuesta = new EventoResponse(
            1L,
            "Concierto de prueba",
            "Descripción de prueba",
            TipoEvento.RECITAL,
            EstadoEvento.BORRADOR,
            1L,
            1L,
            LocalDateTime.of(2027, 12, 20, 20, 0),
            List.of(),
            4,
            false,
            null
    );

    when(eventoService.crear(eq(1L), any(EventoRequest.class)))
            .thenReturn(respuesta);

    mockMvc.perform(post("/eventos")
            .header("X-User-Id", 1)
            .header("X-User-Rol", "ORGANIZADOR")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "nombre": "Concierto de prueba",
                  "descripcion": "Descripción de prueba",
                  "tipo": "RECITAL",
                  "lugarId": 1,
                  "fechaHora": "2027-12-20T20:00:00"
                }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.estado").value("BORRADOR"));

    verify(eventoService).crear(eq(1L), any(EventoRequest.class));
}
@Test
void crearEventoQueViolaReglaDeNegocioDevuelve422() throws Exception {

    when(eventoService.crear(eq(1L), any(EventoRequest.class)))
            .thenThrow(new BusinessRuleException(
                    "REGLA_NEGOCIO",
                    "No se puede crear el evento."
            ));

    mockMvc.perform(post("/eventos")
            .header("X-User-Id", 1)
            .header("X-User-Rol", "ORGANIZADOR")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "nombre": "Concierto de prueba",
                  "tipo": "RECITAL",
                  "lugarId": 1,
                  "fechaHora": "2027-12-20T20:00:00"
                }
                """))
            .andExpect(status().isUnprocessableEntity());

    verify(eventoService).crear(eq(1L), any(EventoRequest.class));
}
}
