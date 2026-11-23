package com.ticketya.fila.controller;

import com.ticketya.fila.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ingreso, idempotencia, roles del cliente y consulta de la posicion (spec fila-virtual). */
class IngresoFilaTest extends IntegracionTest {

    // ------------------------------------------------------------------ Ingreso a la fila

    @Test
    @DisplayName("Ingreso a la fila: Primer cliente en una fila vacía")
    void primerCliente() throws Exception {
        activarFila(EVENTO, 10, 30);

        ingresar(EVENTO, "101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(42))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.posicion").value(1))
                .andExpect(jsonPath("$.habilitado").value(false))
                .andExpect(jsonPath("$.habilitadoHasta").doesNotExist());
    }

    @Test
    @DisplayName("Ingreso a la fila: Ingresos sucesivos")
    void ingresosSucesivos() throws Exception {
        activarFila(EVENTO, 10, 30);

        ingresar(EVENTO, "101").andExpect(jsonPath("$.posicion").value(1));
        ingresar(EVENTO, "102").andExpect(jsonPath("$.posicion").value(2));
        ingresar(EVENTO, "103").andExpect(jsonPath("$.posicion").value(3));
    }

    @Test
    @DisplayName("Ingreso a una fila inactiva: Evento sin fila")
    void eventoSinFila() throws Exception {
        ingresar(99L, "101")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILA_NO_ACTIVA"));

        assertThat(enEspera(99L)).isZero();
        assertThat(logs.operaciones("INGRESO_FILA")).isEmpty();
    }

    // ------------------------------------------------------------------ Rol del cliente en la fila

    @Test
    @DisplayName("Rol del cliente en la fila: Organizador intenta ingresar")
    void organizadorIntentaIngresar() throws Exception {
        activarFila(EVENTO, 10, 30);

        ingresar(EVENTO, "300", "ORGANIZADOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        assertThat(enEspera(EVENTO)).isZero();
    }

    @Test
    @DisplayName("Rol del cliente en la fila: Ingreso sin identidad")
    void ingresoSinIdentidad() throws Exception {
        activarFila(EVENTO, 10, 30);

        ingresar(EVENTO, null, "CLIENTE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        assertThat(enEspera(EVENTO)).isZero();
    }

    // ------------------------------------------------------------------ Ingreso idempotente

    @Test
    @DisplayName("Ingreso idempotente: Ingreso repetido en espera")
    void ingresoRepetidoEnEspera() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");

        ingresar(EVENTO, "102")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.posicion").value(2));

        assertThat(enEspera(EVENTO)).isEqualTo(2);
    }

    @Test
    @DisplayName("Ingreso idempotente: Ingreso repetido de un habilitado")
    void ingresoRepetidoDeUnHabilitado() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(30));
        String hasta = cuerpo(posicion(EVENTO, "101")).get("habilitadoHasta").asText();

        ingresar(EVENTO, "101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("HABILITADO"))
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.habilitadoHasta").value(hasta))
                .andExpect(jsonPath("$.posicion").doesNotExist());

        assertThat(enEspera(EVENTO)).isZero();
        assertThat(habilitados(EVENTO)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Registro de operaciones

    @Test
    @DisplayName("Registro de operaciones críticas de la fila: Ingreso nuevo registrado")
    void ingresoNuevoRegistrado() throws Exception {
        activarFila(EVENTO, 10, 30);

        ingresar(EVENTO, "101");

        assertThat(logs.operaciones("INGRESO_FILA")).singleElement()
                .satisfies(l -> assertThat(l)
                        .containsEntry("usuario", "101")
                        .containsEntry("eventoId", "42")
                        .containsEntry("clienteId", "101")
                        .containsEntry("posicion", "1")
                        .containsKey("correlationId"));
    }

    @Test
    @DisplayName("Registro de operaciones críticas de la fila: Ingreso repetido no registrado")
    void ingresoRepetidoNoRegistrado() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");

        ingresar(EVENTO, "101");
        ingresar(EVENTO, "101");

        assertThat(logs.operaciones("INGRESO_FILA")).hasSize(1);
    }

    // ------------------------------------------------------------------ Consulta de la posición

    @Test
    @DisplayName("Consulta de la posición: Cliente en espera")
    void consultaEnEspera() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");
        ingresar(EVENTO, "103");

        posicion(EVENTO, "103")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.posicion").value(3))
                .andExpect(jsonPath("$.habilitado").value(false));
    }

    @Test
    @DisplayName("Consulta de la posición: Cliente habilitado")
    void consultaHabilitado() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(30));   // habilitado a las 20:00:30

        posicion(EVENTO, "101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("HABILITADO"))
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.habilitadoHasta").value("2026-12-01T20:10:30"));
    }

    @Test
    @DisplayName("Consulta de la posición: Cliente que no ingresó")
    void consultaClienteQueNoIngreso() throws Exception {
        activarFila(EVENTO, 10, 30);

        posicion(EVENTO, "200")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.estado").value("FUERA_DE_FILA"))
                .andExpect(jsonPath("$.habilitado").value(false));
    }

    @Test
    @DisplayName("Consulta de la posición: Evento sin fila activa")
    void consultaEventoSinFila() throws Exception {
        posicion(99L, "101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.estado").value("SIN_FILA"))
                .andExpect(jsonPath("$.habilitado").value(true));
    }
}
