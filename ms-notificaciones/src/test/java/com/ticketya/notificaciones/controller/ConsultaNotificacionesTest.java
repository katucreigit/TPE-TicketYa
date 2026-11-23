package com.ticketya.notificaciones.controller;

import com.ticketya.notificaciones.IntegracionTest;
import com.ticketya.notificaciones.model.Destinatario;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Grupo 6: GET /notificaciones y GET /notificaciones/{id}. Los documentos se insertan ya ENVIADA o FALLIDA para que
 * el procesador no los modifique durante la prueba.
 */
class ConsultaNotificacionesTest extends IntegracionTest {

    private static final Instant BASE = Instant.parse("2026-11-01T10:00:00Z");

    @Nested
    @DisplayName("Listado de notificaciones para administradores")
    class Listado {

        @Test
        @DisplayName("Listado sin filtros")
        void listadoSinFiltros() throws Exception {
            String vieja = guardar("101", TipoNotificacion.COMPRA_CONFIRMADA, EstadoNotificacion.ENVIADA, 0).getId();
            String media = guardar("102", TipoNotificacion.EVENTO_CANCELADO, EstadoNotificacion.FALLIDA, 1).getId();
            String nueva = guardar("103", TipoNotificacion.EVENTO_MODIFICADO, EstadoNotificacion.ENVIADA, 2).getId();

            mvc.perform(admin(get("/notificaciones")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.content[0].id").value(nueva))
                    .andExpect(jsonPath("$.content[1].id").value(media))
                    .andExpect(jsonPath("$.content[2].id").value(vieja))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("Filtros combinados")
        void filtrosCombinados() throws Exception {
            String buscada = guardar("101", TipoNotificacion.COMPRA_CONFIRMADA, EstadoNotificacion.FALLIDA, 0).getId();
            guardar("101", TipoNotificacion.COMPRA_CONFIRMADA, EstadoNotificacion.ENVIADA, 1);
            guardar("101", TipoNotificacion.EVENTO_CANCELADO, EstadoNotificacion.FALLIDA, 2);
            guardar("999", TipoNotificacion.COMPRA_CONFIRMADA, EstadoNotificacion.FALLIDA, 3);

            mvc.perform(admin(get("/notificaciones")
                            .param("usuarioId", "101").param("tipo", "COMPRA_CONFIRMADA").param("estado", "FALLIDA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(buscada))
                    .andExpect(jsonPath("$.content[*].destinatario.usuarioId", everyItem(is("101"))))
                    .andExpect(jsonPath("$.content[*].tipo", everyItem(is("COMPRA_CONFIRMADA"))))
                    .andExpect(jsonPath("$.content[*].estado", everyItem(is("FALLIDA"))));
        }

        @Test
        @DisplayName("Paginacion")
        void paginacion() throws Exception {
            for (int i = 0; i < 25; i++) {
                guardar("101", TipoNotificacion.COMPRA_CONFIRMADA, EstadoNotificacion.ENVIADA, i);
            }

            mvc.perform(admin(get("/notificaciones").param("page", "1").param("size", "10")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(10)))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }

        @ParameterizedTest
        @CsvSource({"size,0", "size,101", "page,-1", "estado,PERDIDA", "tipo,OTRO"})
        @DisplayName("Parametros invalidos")
        void parametrosInvalidos(String parametro, String valor) throws Exception {
            mvc.perform(admin(get("/notificaciones").param(parametro, valor)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
        }
    }

    @Nested
    @DisplayName("Detalle de una notificacion para administradores")
    class Detalle {

        @Test
        @DisplayName("Notificacion existente")
        void notificacionExistente() throws Exception {
            Notificacion n = guardar("101", TipoNotificacion.EVENTO_MODIFICADO, EstadoNotificacion.FALLIDA, 0);

            mvc.perform(admin(get("/notificaciones/{id}", n.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(n.getId()))
                    .andExpect(jsonPath("$.tipo").value("EVENTO_MODIFICADO"))
                    .andExpect(jsonPath("$.destinatario.usuarioId").value("101"))
                    .andExpect(jsonPath("$.destinatario.email").value("u101@example.com"))
                    .andExpect(jsonPath("$.datos.eventoId").value(42))
                    .andExpect(jsonPath("$.datos.cambios.lugar.despues").value("Estadio Sur"))
                    .andExpect(jsonPath("$.estado").value("FALLIDA"))
                    .andExpect(jsonPath("$.intentos").value(3))
                    .andExpect(jsonPath("$.ultimoError").value("rechazado"))
                    .andExpect(jsonPath("$.claveDeduplicacion").value(n.getClaveDeduplicacion()))
                    .andExpect(jsonPath("$.correlationId").value("corr-" + n.getClaveDeduplicacion()))
                    .andExpect(jsonPath("$.creadaEn").value("2026-11-01T10:00:00Z"))
                    .andExpect(jsonPath("$.actualizadaEn").value("2026-11-01T10:00:00Z"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"6741f0c2a1b2c3d4e5f60718", "no-es-un-objectid"})
        @DisplayName("Notificacion inexistente")
        void notificacionInexistente(String id) throws Exception {
            mvc.perform(admin(get("/notificaciones/{id}", id)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
        }
    }

    @Nested
    @DisplayName("Autorizacion de la consulta")
    class Autorizacion {

        @ParameterizedTest
        @CsvSource({"CLIENTE,listado", "ORGANIZADOR,listado", "PERSONAL_ACCESO,listado",
                "CLIENTE,detalle", "ORGANIZADOR,detalle", "PERSONAL_ACCESO,detalle"})
        @DisplayName("Rol distinto de ADMIN")
        void rolDistintoDeAdmin(String rol, String endpoint) throws Exception {
            String id = guardar("101", TipoNotificacion.COMPRA_CONFIRMADA, EstadoNotificacion.ENVIADA, 0).getId();
            MockHttpServletRequestBuilder req = "listado".equals(endpoint)
                    ? get("/notificaciones") : get("/notificaciones/{id}", id);

            mvc.perform(req.header("X-User-Id", "5").header("X-User-Rol", rol))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
        }

        @Test
        @DisplayName("Headers de identidad ausentes")
        void headersDeIdentidadAusentes() throws Exception {
            mvc.perform(get("/notificaciones").header("X-User-Rol", "ADMIN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
            mvc.perform(get("/notificaciones").header("X-User-Id", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
            mvc.perform(get("/notificaciones"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
        }
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder req) {
        return req.header("X-User-Id", "1").header("X-User-Rol", "ADMIN");
    }

    /** Inserta una notificacion ya procesada; {@code orden} separa las fechas de creacion por minutos. */
    private Notificacion guardar(String usuarioId, TipoNotificacion tipo, EstadoNotificacion estado, int orden) {
        Instant creada = BASE.plusSeconds(60L * orden);
        Notificacion n = new Notificacion();
        n.setTipo(tipo);
        n.setDestinatario(new Destinatario(usuarioId, "u" + usuarioId + "@example.com"));
        n.setDatos(Map.of("eventoId", 42, "cambios", Map.of("lugar", Map.of("antes", "Teatro Norte", "despues", "Estadio Sur"))));
        n.setEstado(estado);
        n.setIntentos(estado == EstadoNotificacion.FALLIDA ? 3 : 1);
        n.setUltimoError(estado == EstadoNotificacion.FALLIDA ? "rechazado" : null);
        n.setClaveDeduplicacion("clave-" + usuarioId + "-" + orden + "-" + tipo + "-" + estado);
        n.setCorrelationId("corr-" + n.getClaveDeduplicacion());
        n.setCreadaEn(creada);
        n.setActualizadaEn(creada);
        if (estado == EstadoNotificacion.ENVIADA) {
            n.setEnviadaEn(creada);
        }
        return mongo.insert(n);
    }
}
