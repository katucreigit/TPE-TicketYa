package com.ticketya.fila.controller;

import com.ticketya.fila.IntegracionTest;
import com.ticketya.fila.repository.FilaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activacion, reactivacion, validacion, desactivacion y roles de administracion (spec fila-virtual). */
class ActivacionFilaTest extends IntegracionTest {

    @Test
    @DisplayName("Activación de la fila de un evento: Activar una fila con parámetros")
    void activarConParametros() throws Exception {
        activar(EVENTO, "{\"tamanoTanda\":50,\"segundosEntreTandas\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(42))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.tamanoTanda").value(50))
                .andExpect(jsonPath("$.segundosEntreTandas").value(30))
                .andExpect(jsonPath("$.minutosVentana").value(10))
                .andExpect(jsonPath("$.enEspera").value(0))
                .andExpect(jsonPath("$.habilitados").value(0))
                .andExpect(jsonPath("$.proximaTandaEn").value("2026-12-01T20:00:30"));

        assertThat(logs.operaciones("FILA_ACTIVADA")).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("eventoId", "42")
                        .containsEntry("usuario", "900")
                        .containsEntry("tamanoTanda", "50")
                        .containsEntry("segundosEntreTandas", "30"));
    }

    @Test
    @DisplayName("Activación de la fila de un evento: Activar sin cuerpo usa los valores por defecto")
    void activarSinCuerpo() throws Exception {
        activar(EVENTO, null, "300", "ORGANIZADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.tamanoTanda").value(100))
                .andExpect(jsonPath("$.segundosEntreTandas").value(60));
    }

    @Test
    @DisplayName("Activación de la fila de un evento: las claves de la fila tienen TTL de seguridad")
    void lasClavesTienenTtl() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101").andExpect(status().isOk());

        for (String clave : new String[]{FilaRepository.config(EVENTO), FilaRepository.espera(EVENTO),
                FilaRepository.secuencia(EVENTO)}) {
            Long ttl = redis.getExpire(clave);
            assertThat(ttl).as("TTL de %s", clave).isBetween(23 * 3600L, 24 * 3600L);
        }
    }

    @Test
    @DisplayName("Reactivación de una fila activa: Cambiar el tamaño de tanda sin perder la fila")
    void reactivarConservaLaFila() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");
        ingresar(EVENTO, "103");

        activar(EVENTO, "{\"tamanoTanda\":200,\"segundosEntreTandas\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanoTanda").value(200))
                .andExpect(jsonPath("$.enEspera").value(3))
                .andExpect(jsonPath("$.proximaTandaEn").value("2026-12-01T20:00:30"));

        posicion(EVENTO, "101").andExpect(jsonPath("$.posicion").value(1));
        posicion(EVENTO, "102").andExpect(jsonPath("$.posicion").value(2));
        posicion(EVENTO, "103").andExpect(jsonPath("$.posicion").value(3));
    }

    @Test
    @DisplayName("Validación de los parámetros de la fila: Tamaño de tanda cero")
    void tamanoDeTandaCero() throws Exception {
        activar(EVENTO, "{\"tamanoTanda\":0,\"segundosEntreTandas\":30}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        posicion(EVENTO, "101").andExpect(jsonPath("$.activa").value(false));
    }

    @Test
    @DisplayName("Validación de los parámetros de la fila: Intervalo mayor al máximo")
    void intervaloMayorAlMaximo() throws Exception {
        activar(EVENTO, "{\"tamanoTanda\":10,\"segundosEntreTandas\":7200}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        posicion(EVENTO, "101").andExpect(jsonPath("$.activa").value(false));
    }

    @Test
    @DisplayName("Validación de los parámetros de la fila: cuerpo mal formado")
    void cuerpoMalFormado() throws Exception {
        activar(EVENTO, "{\"tamanoTanda\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    @DisplayName("Desactivación de la fila de un evento: Desactivar una fila con clientes")
    void desactivarConClientes() throws Exception {
        activarFila(EVENTO, 1, 30);
        ingresar(EVENTO, "101");
        ingresar(EVENTO, "102");
        pasar(java.time.Duration.ofSeconds(30));   // 101 habilitado, 102 en espera

        desactivar(EVENTO, "300", "ORGANIZADOR").andExpect(status().isNoContent());

        for (String cliente : new String[]{"101", "102"}) {
            estadoInterno(EVENTO, cliente)
                    .andExpect(jsonPath("$.activa").value(false))
                    .andExpect(jsonPath("$.habilitado").value(true));
        }
        assertThat(enEspera(EVENTO)).isZero();
        assertThat(habilitados(EVENTO)).isZero();
        assertThat(redis.opsForSet().isMember(FilaRepository.ACTIVAS, "42")).isFalse();
        assertThat(logs.operaciones("FILA_DESACTIVADA")).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("enEspera", "1").containsEntry("habilitados", "1"));
    }

    @Test
    @DisplayName("Desactivación de la fila de un evento: Desactivar una fila inactiva")
    void desactivarFilaInactiva() throws Exception {
        desactivar(99L, "900", "ADMIN").andExpect(status().isNoContent());

        assertThat(logs.operaciones("FILA_DESACTIVADA")).isEmpty();
    }

    @Test
    @DisplayName("Rol para administrar la fila: Cliente intenta activar")
    void clienteIntentaActivar() throws Exception {
        activar(EVENTO, "{\"tamanoTanda\":10,\"segundosEntreTandas\":30}", "101", "CLIENTE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        posicion(EVENTO, "101").andExpect(jsonPath("$.activa").value(false));
    }

    @Test
    @DisplayName("Rol para administrar la fila: Activar sin identidad")
    void activarSinIdentidad() throws Exception {
        activar(EVENTO, null, "900", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    @DisplayName("Rol para administrar la fila: personal de acceso no puede desactivar")
    void personalDeAccesoNoDesactiva() throws Exception {
        activarFila(EVENTO, 10, 30);

        desactivar(EVENTO, "500", "PERSONAL_ACCESO")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        posicion(EVENTO, "101").andExpect(jsonPath("$.activa").value(true));
    }
}
