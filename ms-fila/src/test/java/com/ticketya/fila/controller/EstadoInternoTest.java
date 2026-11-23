package com.ticketya.fila.controller;

import com.ticketya.fila.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consulta interna del estado de un cliente, que usa ms-reservas (spec fila-virtual). */
class EstadoInternoTest extends IntegracionTest {

    @Test
    @DisplayName("Consulta interna del estado de un cliente: Fila inactiva")
    void filaInactiva() throws Exception {
        estadoInterno(99L, "101")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"activa\":false,\"habilitado\":true}", true));
    }

    @Test
    @DisplayName("Consulta interna del estado de un cliente: Cliente en espera")
    void clienteEnEspera() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");
        ingresar(EVENTO, "103");

        estadoInterno(EVENTO, "103")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"activa\":true,\"habilitado\":false}", true));
    }

    @Test
    @DisplayName("Consulta interna del estado de un cliente: cliente que no ingresó")
    void clienteQueNoIngreso() throws Exception {
        activarFila(EVENTO, 10, 30);

        estadoInterno(EVENTO, "200")
                .andExpect(content().json("{\"activa\":true,\"habilitado\":false}", true));
    }

    @Test
    @DisplayName("Consulta interna del estado de un cliente: Cliente habilitado dentro de su ventana")
    void habilitadoDentroDeSuVentana() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(30));
        reloj.avanzar(Duration.ofMinutes(3));

        estadoInterno(EVENTO, "101")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"activa\":true,\"habilitado\":true}", true));
    }

    @Test
    @DisplayName("Consulta interna del estado de un cliente: Ventana vencida aunque el proceso no haya corrido")
    void ventanaVencidaSinProceso() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(30));
        reloj.avanzar(Duration.ofMinutes(10));   // justo en habilitadoHasta: ya no esta habilitado

        estadoInterno(EVENTO, "101")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"activa\":true,\"habilitado\":false}", true));
    }
}
