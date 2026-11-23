package com.ticketya.fila.service;

import com.ticketya.fila.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Habilitacion por tandas y vencimiento del turno, con el reloj controlable (spec fila-virtual). */
class TandasFilaTest extends IntegracionTest {

    // ------------------------------------------------------------------ Habilitación por tandas

    @Test
    @DisplayName("Habilitación por tandas: Tanda completa")
    void tandaCompleta() throws Exception {
        activarFila(EVENTO, 2, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");
        ingresar(EVENTO, "103");

        pasar(Duration.ofSeconds(30));

        for (String cliente : new String[]{"101", "102"}) {
            posicion(EVENTO, cliente)
                    .andExpect(jsonPath("$.estado").value("HABILITADO"))
                    .andExpect(jsonPath("$.habilitadoHasta").value("2026-12-01T20:10:30"));
        }
        posicion(EVENTO, "103")
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.posicion").value(1));
        assertThat(logs.operaciones("CLIENTE_HABILITADO"))
                .extracting(l -> l.get("clienteId")).containsExactly("101", "102");
        assertThat(logs.operaciones("CLIENTE_HABILITADO").getFirst())
                .containsEntry("usuario", "SISTEMA")
                .containsEntry("habilitadoHasta", "2026-12-01T20:10:30");
    }

    @Test
    @DisplayName("Habilitación por tandas: Antes de tiempo no se habilita")
    void antesDeTiempoNoSeHabilita() throws Exception {
        activarFila(EVENTO, 2, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");

        pasar(Duration.ofSeconds(29));

        assertThat(habilitados(EVENTO)).isZero();
        assertThat(enEspera(EVENTO)).isEqualTo(2);
        posicion(EVENTO, "101").andExpect(jsonPath("$.estado").value("EN_ESPERA"));
    }

    @Test
    @DisplayName("Habilitación por tandas: Tanda con menos clientes que el tamaño")
    void tandaConMenosClientes() throws Exception {
        activarFila(EVENTO, 100, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");
        ingresar(EVENTO, "103");

        pasar(Duration.ofSeconds(30));

        assertThat(habilitados(EVENTO)).isEqualTo(3);
        assertThat(enEspera(EVENTO)).isZero();
    }

    @Test
    @DisplayName("Habilitación por tandas: Tandas sucesivas")
    void tandasSucesivas() throws Exception {
        activarFila(EVENTO, 2, 30);
        for (String c : List.of("101", "102", "103", "104", "105")) {
            ingresar(EVENTO, c);
        }

        pasar(Duration.ofSeconds(30));
        pasar(Duration.ofSeconds(10));   // en el medio del intervalo no hay tanda
        pasar(Duration.ofSeconds(20));

        assertThat(logs.operaciones("CLIENTE_HABILITADO"))
                .extracting(l -> l.get("clienteId")).containsExactly("101", "102", "103", "104");
        posicion(EVENTO, "105")
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.posicion").value(1));
    }

    @Test
    @DisplayName("Habilitación por tandas: la tanda avanza aunque la fila esté vacía")
    void tandaConFilaVacia() throws Exception {
        activarFila(EVENTO, 2, 30);

        pasar(Duration.ofSeconds(30));   // tanda vacia: la proxima queda a los 60 s
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(29));
        assertThat(habilitados(EVENTO)).isZero();

        pasar(Duration.ofSeconds(1));
        assertThat(habilitados(EVENTO)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Vencimiento del turno

    @Test
    @DisplayName("Vencimiento del turno: Turno vencido sin comprar")
    void turnoVencidoSinComprar() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(30));

        pasar(Duration.ofMinutes(10));

        posicion(EVENTO, "101")
                .andExpect(jsonPath("$.estado").value("FUERA_DE_FILA"))
                .andExpect(jsonPath("$.habilitado").value(false));
        estadoInterno(EVENTO, "101").andExpect(jsonPath("$.habilitado").value(false));
        assertThat(habilitados(EVENTO)).isZero();
        assertThat(logs.operaciones("TURNO_VENCIDO")).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("clienteId", "101").containsEntry("usuario", "SISTEMA"));
    }

    @Test
    @DisplayName("Vencimiento del turno: Reingreso después de perder el turno")
    void reingresoDespuesDePerderElTurno() throws Exception {
        activarFila(EVENTO, 1, 30);
        ingresar(EVENTO, "101");
        pasar(Duration.ofSeconds(30));                 // 101 habilitado
        for (String c : List.of("102", "103", "104", "105")) {
            ingresar(EVENTO, c);
        }
        reloj.avanzar(Duration.ofMinutes(10));        // la ventana vence; el proceso no corre

        ingresar(EVENTO, "101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.posicion").value(5));

        // El vencimiento lo detecto el ingreso: se registra una vez y el proceso no lo repite.
        procesador.procesar();
        List<Map<String, String>> vencidos = logs.operaciones("TURNO_VENCIDO");
        assertThat(vencidos).singleElement().satisfies(l -> assertThat(l).containsEntry("clienteId", "101"));
        assertThat(logs.operaciones("INGRESO_FILA"))
                .filteredOn(l -> "101".equals(l.get("clienteId"))).hasSize(2);
    }
}
