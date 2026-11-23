package com.ticketya.recomendaciones.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.recomendaciones.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recomendaciones personalizadas, sin historial y "también compraron" (spec preferencias-recomendaciones). */
class RecomendacionesTest extends IntegracionTest {

    // ------------------------------------------------------------------ Recomendaciones personalizadas

    @Test
    @DisplayName("Recomendaciones personalizadas: Eventos ordenados por puntaje")
    void eventosOrdenadosPorPuntaje() throws Exception {
        MS_EVENTOS.publicado(1, "RECITAL", "Rosario", FECHA, "Banda X");
        MS_EVENTOS.publicado(2, "TEATRO", "Azul", FECHA.plusDays(10), "Elenco");
        compro("201", 1L);
        compro("202", 1L);
        putPreferencias("101", "{\"tipos\":[\"TEATRO\"]}");

        JsonNode r = cuerpo(recomendaciones("101").andExpect(status().isOk()));

        assertThat(r.get("degradado").asBoolean()).isFalse();
        assertThat(ids(r)).containsExactly(2L, 1L);
        JsonNode teatro = r.get("eventos").get(0);
        assertThat(teatro.get("puntaje").asDouble()).isEqualTo(3.0);
        assertThat(teatro.get("motivos").toString()).isEqualTo("[\"TIPO\"]");
        assertThat(teatro.get("nombre").asText()).isEqualTo("Evento 2");
        assertThat(teatro.get("tipo").asText()).isEqualTo("TEATRO");
        assertThat(teatro.get("ciudad").asText()).isEqualTo("Azul");
        assertThat(teatro.get("fechaHora").asText()).isEqualTo("2027-03-11T21:00:00");
        assertThat(teatro.get("participantes").get(0).asText()).isEqualTo("Elenco");
        JsonNode recital = r.get("eventos").get(1);
        assertThat(recital.get("puntaje").asDouble()).isEqualTo(1.0);
        assertThat(recital.get("motivos").toString()).isEqualTo("[\"POPULAR\"]");
    }

    @Test
    @DisplayName("Recomendaciones personalizadas: Eventos ya comprados excluidos")
    void eventosYaCompradosExcluidos() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        MS_EVENTOS.publicado(43, "RECITAL", "Tandil", FECHA);
        compro("101", 42L);

        assertThat(ids(cuerpo(recomendaciones("101")))).containsExactly(43L);
    }

    @Test
    @DisplayName("Recomendaciones personalizadas: Solo eventos publicados")
    void soloEventosPublicados() throws Exception {
        MS_EVENTOS.evento(50, "RECITAL", "AGOTADO", "Tandil", FECHA);
        MS_EVENTOS.evento(51, "RECITAL", "SUSPENDIDO", "Tandil", FECHA);
        MS_EVENTOS.publicado(52, "TEATRO", "Azul", FECHA);
        putPreferencias("101", "{\"tipos\":[\"RECITAL\"],\"ciudad\":\"Tandil\"}");

        assertThat(ids(cuerpo(recomendaciones("101")))).containsExactly(52L);
    }

    @Test
    @DisplayName("Recomendaciones personalizadas: Límite fuera de rango")
    void limiteFueraDeRango() throws Exception {
        for (String limite : List.of("0", "51", "abc")) {
            mvc.perform(identidad(get("/recomendaciones").param("limite", limite), "101", "CLIENTE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
        }
    }

    @Test
    @DisplayName("Recomendaciones personalizadas: límite por defecto 10 y límite explícito")
    void limite() throws Exception {
        for (long id = 1; id <= 12; id++) {
            MS_EVENTOS.publicado(id, "TEATRO", "Azul", FECHA.plusDays(id));
        }

        assertThat(ids(cuerpo(recomendaciones("101")))).hasSize(10);
        JsonNode tres = cuerpo(mvc.perform(identidad(get("/recomendaciones").param("limite", "3"), "101", "CLIENTE")));
        assertThat(ids(tres)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Recomendaciones personalizadas: compras parecidas suman TAMBIEN_COMPRARON")
    void comprasParecidas() throws Exception {
        MS_EVENTOS.publicado(10, "RECITAL", "Tandil", FECHA);
        MS_EVENTOS.publicado(42, "TEATRO", "Azul", FECHA.plusDays(2));
        MS_EVENTOS.publicado(43, "TEATRO", "Azul", FECHA.plusDays(1));
        compro("101", 10L);
        compro("102", 10L, 42L);
        compro("103", 10L, 42L);
        compro("104", 43L);
        compro("105", 43L);
        compro("106", 43L);

        JsonNode r = cuerpo(recomendaciones("101"));

        // 42: 2 x 2/2 + 1 x 2/3 = 2.67; 43: 1 x 3/3 = 1.0
        assertThat(ids(r)).containsExactly(42L, 43L);
        assertThat(r.get("eventos").get(0).get("puntaje").asDouble()).isEqualTo(2.67);
        assertThat(r.get("eventos").get(0).get("motivos").toString()).isEqualTo("[\"TAMBIEN_COMPRARON\",\"POPULAR\"]");
        assertThat(r.get("eventos").get(1).get("puntaje").asDouble()).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------ Sin preferencias ni historial

    @Test
    @DisplayName("Recomendaciones sin preferencias ni historial: Cliente nuevo con compras de otros")
    void clienteNuevoConComprasDeOtros() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        MS_EVENTOS.publicado(43, "TEATRO", "Azul", FECHA.plusDays(5));
        compro("301", 42L);
        compro("302", 42L);
        for (int i = 1; i <= 5; i++) {
            compro("40" + i, 43L);
        }

        JsonNode r = cuerpo(recomendaciones("202"));

        assertThat(ids(r)).containsExactly(43L, 42L);
        assertThat(r.get("eventos").get(0).get("motivos").toString()).isEqualTo("[\"POPULAR\"]");
        assertThat(r.get("eventos").get(1).get("puntaje").asDouble()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("Recomendaciones sin preferencias ni historial: Sistema sin compras")
    void sistemaSinCompras() throws Exception {
        MS_EVENTOS.publicado(7, "TEATRO", "Azul", FECHA.plusDays(3));
        MS_EVENTOS.publicado(5, "RECITAL", "Tandil", FECHA.plusDays(1));
        MS_EVENTOS.publicado(9, "DEPORTE", "Azul", FECHA.plusDays(2));

        JsonNode r = cuerpo(recomendaciones("202"));

        assertThat(ids(r)).containsExactly(5L, 9L, 7L);
        r.get("eventos").forEach(e -> {
            assertThat(e.get("puntaje").asDouble()).isEqualTo(0.0);
            assertThat(e.get("motivos")).isEmpty();
        });
    }

    // ------------------------------------------------------------------ También compraron

    @Test
    @DisplayName("También compraron: Eventos comprados en común")
    void eventosCompradosEnComun() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        MS_EVENTOS.publicado(43, "RECITAL", "Tandil", FECHA);
        MS_EVENTOS.publicado(44, "RECITAL", "Tandil", FECHA);
        compro("101", 42L, 43L, 44L);
        compro("102", 42L, 43L);

        JsonNode r = cuerpo(tambienCompraron(42).andExpect(status().isOk()));

        assertThat(r.get("eventoId").asLong()).isEqualTo(42);
        assertThat(r.get("degradado").asBoolean()).isFalse();
        assertThat(ids(r)).containsExactly(43L, 44L);
        assertThat(r.get("eventos").get(0).get("compradoresEnComun").asLong()).isEqualTo(2);
        assertThat(r.get("eventos").get(1).get("compradoresEnComun").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("También compraron: no incluye eventos que ya no están publicados")
    void tambienCompraronSoloPublicados() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        MS_EVENTOS.evento(43, "RECITAL", "CANCELADO", "Tandil", FECHA);
        compro("101", 42L, 43L);

        assertThat(ids(cuerpo(tambienCompraron(42)))).isEmpty();
    }

    @Test
    @DisplayName("También compraron: Evento sin compradores")
    void eventoSinCompradores() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        compro("101", 42L);

        tambienCompraron(99)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos").isEmpty());
    }

    private static List<Long> ids(JsonNode respuesta) {
        List<Long> ids = new ArrayList<>();
        respuesta.get("eventos").forEach(e -> ids.add(e.get("eventoId").asLong()));
        return ids;
    }
}
