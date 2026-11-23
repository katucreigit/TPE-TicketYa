package com.ticketya.recomendaciones.trazabilidad;

import com.ticketya.recomendaciones.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Identificador de correlación en ms-recomendaciones (spec preferencias-recomendaciones). */
class TrazabilidadRecomendacionesTest extends IntegracionTest {

    @Test
    @DisplayName("Identificador de correlación en ms-recomendaciones: Propagación a ms-eventos")
    void propagacionAMsEventos() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);

        try (CapturaLogs logs = new CapturaLogs(RegistroSolicitudesFilter.class)) {
            MvcResult r = mvc.perform(get("/recomendaciones")
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .header("X-Correlation-Id", "abc-123"))
                    .andReturn();

            assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo("abc-123");
            Map<String, String> linea = logs.campos("HTTP_REQUEST").getLast();
            assertThat(linea)
                    .containsEntry("method", "GET")
                    .containsEntry("endpoint", "/recomendaciones")
                    .containsEntry("user", "101")
                    .containsEntry("rol", "CLIENTE")
                    .containsEntry("status", "200")
                    .containsEntry("correlationId", "abc-123")
                    .containsKey("durationMs");
        }
        assertThat(MS_EVENTOS.pedidos()).singleElement()
                .satisfies(p -> assertThat(p.correlationId()).isEqualTo("abc-123"));
    }

    @Test
    void sinIdentificadorSeGeneraUno() throws Exception {
        String generado = mvc.perform(get("/preferencias").header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");

        assertThat(generado).isNotBlank();
    }
}
