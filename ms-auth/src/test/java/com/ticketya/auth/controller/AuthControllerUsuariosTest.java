package com.ticketya.auth.controller;

import com.ticketya.auth.config.SecurityConfig;
import com.ticketya.auth.dto.UsuarioResponse;
import com.ticketya.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Listado de usuarios solo para ADMIN (fix-usuarios-solo-admin, spec user-auth). Sin base: el servicio es simulado. */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerUsuariosTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("Listado de usuarios solo para ADMIN: Administrador lista usuarios")
    void administradorListaUsuarios() throws Exception {
        when(authService.listarUsuarios()).thenReturn(List.of(
                new UsuarioResponse(1L, "organizador@ticketya.com", "ORGANIZADOR"),
                new UsuarioResponse(2L, "cliente@ticketya.com", "CLIENTE")));

        mvc.perform(get("/auth/usuarios").header("X-User-Rol", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":1,\"email\":\"organizador@ticketya.com\",\"rol\":\"ORGANIZADOR\"},"
                        + "{\"id\":2,\"email\":\"cliente@ticketya.com\",\"rol\":\"CLIENTE\"}]", true));
    }

    @Test
    @DisplayName("Listado de usuarios solo para ADMIN: Cliente intenta listar usuarios")
    void clienteIntentaListarUsuarios() throws Exception {
        String cuerpo = mvc.perform(get("/auth/usuarios").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("@");
        verify(authService, never()).listarUsuarios();
    }

    @Test
    @DisplayName("Listado de usuarios solo para ADMIN: Listado sin rol")
    void listadoSinRol() throws Exception {
        mvc.perform(get("/auth/usuarios"))
                .andExpect(status().isForbidden());

        verify(authService, never()).listarUsuarios();
    }

    @Test
    @DisplayName("Listado de usuarios solo para ADMIN: otros roles tampoco pueden")
    void otrosRoles() throws Exception {
        for (String rol : List.of("ORGANIZADOR", "PERSONAL_ACCESO", "admin", " ")) {
            mvc.perform(get("/auth/usuarios").header("X-User-Rol", rol))
                    .andExpect(status().isForbidden());
        }
        verify(authService, never()).listarUsuarios();
    }
}
