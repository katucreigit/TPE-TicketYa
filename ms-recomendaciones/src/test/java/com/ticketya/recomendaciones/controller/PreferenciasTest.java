package com.ticketya.recomendaciones.controller;

import com.ticketya.recomendaciones.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Preferencias del cliente, su validación e identidad y roles (spec preferencias-recomendaciones). */
class PreferenciasTest extends IntegracionTest {

    // ------------------------------------------------------------------ Guardar preferencias

    @Test
    @DisplayName("Guardar preferencias: Guardar preferencias completas")
    void guardarPreferenciasCompletas() throws Exception {
        putPreferencias("101", "{\"tipos\":[\"RECITAL\",\"TEATRO\"],\"artistas\":[\"Los Andariegos\"],\"ciudad\":\"Tandil\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value("101"))
                .andExpect(jsonPath("$.tipos[0]").value("RECITAL"))
                .andExpect(jsonPath("$.tipos[1]").value("TEATRO"))
                .andExpect(jsonPath("$.artistas[0]").value("Los Andariegos"))
                .andExpect(jsonPath("$.ciudad").value("Tandil"))
                .andExpect(jsonPath("$.actualizadaEn").value("2026-12-01T20:00:00Z"));
    }

    @Test
    @DisplayName("Guardar preferencias: Reemplazar preferencias anteriores")
    void reemplazarPreferenciasAnteriores() throws Exception {
        putPreferencias("101", "{\"tipos\":[\"RECITAL\"],\"ciudad\":\"Tandil\"}").andExpect(status().isOk());

        putPreferencias("101", "{\"artistas\":[\"Soda\"]}").andExpect(status().isOk());

        getPreferencias("101")
                .andExpect(jsonPath("$.tipos").isEmpty())
                .andExpect(jsonPath("$.artistas[0]").value("Soda"))
                .andExpect(jsonPath("$.ciudad").isEmpty());
    }

    @Test
    @DisplayName("Guardar preferencias: Valores repetidos")
    void valoresRepetidos() throws Exception {
        putPreferencias("101", "{\"tipos\":[\"RECITAL\",\"RECITAL\"],\"artistas\":[\"Soda\",\"soda \"]}")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"tipos\":[\"RECITAL\"],\"artistas\":[\"Soda\"]}"));
    }

    @Test
    @DisplayName("Guardar preferencias: ciudad en blanco se guarda como null")
    void ciudadEnBlanco() throws Exception {
        putPreferencias("101", "{\"ciudad\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad").isEmpty());
    }

    // ------------------------------------------------------------------ Consultar preferencias

    @Test
    @DisplayName("Consultar preferencias: Cliente con preferencias")
    void clienteConPreferencias() throws Exception {
        putPreferencias("101", "{\"tipos\":[\"DEPORTE\"],\"artistas\":[\"Boca\"],\"ciudad\":\"Azul\"}");

        getPreferencias("101")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"clienteId\":\"101\",\"tipos\":[\"DEPORTE\"],\"artistas\":[\"Boca\"],"
                        + "\"ciudad\":\"Azul\",\"actualizadaEn\":\"2026-12-01T20:00:00Z\"}", true));
    }

    @Test
    @DisplayName("Consultar preferencias: Cliente sin preferencias")
    void clienteSinPreferencias() throws Exception {
        getPreferencias("202")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"clienteId\":\"202\",\"tipos\":[],\"artistas\":[],"
                        + "\"ciudad\":null,\"actualizadaEn\":null}", true));
    }

    // ------------------------------------------------------------------ Validación de preferencias

    @Test
    @DisplayName("Validación de preferencias: Tipo de evento inexistente")
    void tipoDeEventoInexistente() throws Exception {
        putPreferencias("101", "{\"tipos\":[\"RECITAL\"]}");

        putPreferencias("101", "{\"tipos\":[\"OPERA\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        getPreferencias("101").andExpect(jsonPath("$.tipos[0]").value("RECITAL"));
    }

    @Test
    @DisplayName("Validación de preferencias: Artista vacío")
    void artistaVacio() throws Exception {
        putPreferencias("101", "{\"artistas\":[\"  \"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        getPreferencias("101").andExpect(jsonPath("$.actualizadaEn").isEmpty());
    }

    @Test
    @DisplayName("Validación de preferencias: Demasiados artistas")
    void demasiadosArtistas() throws Exception {
        String artistas = IntStream.rangeClosed(1, 21).mapToObj(i -> "\"Artista " + i + "\"")
                .collect(Collectors.joining(","));

        putPreferencias("101", "{\"artistas\":[" + artistas + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    @DisplayName("Validación de preferencias: artista y ciudad demasiado largos")
    void textosDemasiadoLargos() throws Exception {
        String largo = "x".repeat(101);
        putPreferencias("101", "{\"artistas\":[\"" + largo + "\"]}").andExpect(status().isBadRequest());
        putPreferencias("101", "{\"ciudad\":\"" + largo + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validación de preferencias: Cuerpo mal formado")
    void cuerpoMalFormado() throws Exception {
        putPreferencias("101", "{\"tipos\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    // ------------------------------------------------------------------ Identidad y roles

    @Test
    @DisplayName("Identidad y roles: Organizador consulta recomendaciones")
    void organizadorConsultaRecomendaciones() throws Exception {
        mvc.perform(identidad(get("/recomendaciones"), "300", "ORGANIZADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("Identidad y roles: Preferencias sin identidad")
    void preferenciasSinIdentidad() throws Exception {
        mvc.perform(identidad(get("/preferencias"), null, "CLIENTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    @DisplayName("Identidad y roles: Organizador consulta también compraron")
    void organizadorConsultaTambienCompraron() throws Exception {
        mvc.perform(identidad(get("/recomendaciones/eventos/42/tambien-compraron"), "300", "ORGANIZADOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Identidad y roles: organizador no puede guardar preferencias")
    void organizadorNoGuardaPreferencias() throws Exception {
        mvc.perform(identidad(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/preferencias"),
                        "300", "ORGANIZADOR")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("Identidad y roles: también compraron sin identidad")
    void tambienCompraronSinIdentidad() throws Exception {
        mvc.perform(get("/recomendaciones/eventos/42/tambien-compraron"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }
}
