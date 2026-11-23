package com.ticketya.fila.trazabilidad;

import com.ticketya.fila.IntegracionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Identificador de correlacion y registro de solicitudes en ms-fila (spec fila-virtual). */
class TrazabilidadFilaTest extends IntegracionTest {

    @Test
    @DisplayName("Identificador de correlación en ms-fila: Solicitud con identificador")
    void solicitudConIdentificador() throws Exception {
        MvcResult r = mvc.perform(get("/fila/eventos/{e}/posicion", EVENTO)
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("X-Correlation-Id", "abc-123"))
                .andReturn();

        assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo("abc-123");
        Map<String, String> linea = logs.campos("HTTP_REQUEST").getLast();
        assertThat(linea).containsEntry("correlationId", "abc-123");
    }

    @Test
    @DisplayName("Identificador de correlación en ms-fila: Solicitud sin identificador")
    void solicitudSinIdentificador() throws Exception {
        activarFila(EVENTO, 10, 30);
        logs.limpiar();

        MvcResult r = ingresar(EVENTO, "101").andReturn();

        String generado = r.getResponse().getHeader("X-Correlation-Id");
        assertThat(generado).isNotBlank();
        assertThat(UUID.fromString(generado)).isNotNull();
        assertThat(logs.campos("HTTP_REQUEST").getLast()).containsEntry("correlationId", generado);
        assertThat(logs.operaciones("INGRESO_FILA").getFirst()).containsEntry("correlationId", generado);
    }

    @Test
    @DisplayName("Registro de solicitudes en ms-fila: Registro de un ingreso")
    void registroDeUnIngreso() throws Exception {
        activarFila(EVENTO, 10, 30);
        logs.limpiar();

        ingresar(EVENTO, "101").andReturn();

        Map<String, String> linea = logs.campos("HTTP_REQUEST").getLast();
        assertThat(linea)
                .containsEntry("method", "POST")
                .containsEntry("endpoint", "/fila/eventos/42/ingresar")
                .containsEntry("user", "101")
                .containsEntry("rol", "CLIENTE")
                .containsEntry("status", "200")
                .containsKey("durationMs")
                .containsKey("correlationId");
    }

    @Test
    @DisplayName("Registro de solicitudes en ms-fila: sin identidad figura ANONIMO")
    void solicitudSinIdentidadEsAnonima() throws Exception {
        estadoInterno(EVENTO, "101").andReturn();

        assertThat(logs.campos("HTTP_REQUEST").getLast())
                .containsEntry("user", "ANONIMO")
                .containsEntry("rol", "ANONIMO");
    }
}
