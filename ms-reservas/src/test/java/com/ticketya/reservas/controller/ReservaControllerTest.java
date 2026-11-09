package com.ticketya.reservas.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.service.VencimientoJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Grupo 2 (reservas) de punta a punta: HTTP, servicio y PostgreSQL real (Testcontainers).
 * Solo se reemplaza el cliente HTTP a ms-eventos. Los tests de concurrencia son del grupo 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReservaControllerTest {

    private static final String INVENTARIO_42 = """
            {"eventoId": 42, "sectores": [
              {"sectorId": 11, "tipo": "NUMERADO", "filas": 5, "asientosPorFila": 20},
              {"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 10}
            ]}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private VencimientoJob vencimientoJob;

    @MockitoBean
    private EventosClient eventosClient;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.execute("TRUNCATE reserva_item, reserva, asiento, inventario_sector, inventario_evento "
                + "RESTART IDENTITY CASCADE");
        mvc.perform(post("/internal/inventarios").contentType(MediaType.APPLICATION_JSON).content(INVENTARIO_42))
                .andExpect(status().isCreated());
    }

    @Nested
    class Creacion {

        @Test
        void reservaAsientosLibresResponde201PendienteConVencimientoA10Minutos() throws Exception {
            JsonNode body = leer(reservar("101", """
                    {"eventoId": 42, "sectorId": 11, "asientos": [{"fila": 3, "numero": 11}, {"fila": 3, "numero": 10}]}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.cantidad").value(2))
                    .andExpect(jsonPath("$.clienteId").value("101"))
                    .andExpect(jsonPath("$.items[0].numero").value(10))
                    .andExpect(jsonPath("$.items[1].numero").value(11)));

            UUID id = UUID.fromString(body.get("reservaId").asText());
            var fila = jdbc.queryForMap("SELECT creada_en, expira_en FROM reserva WHERE id = ?", id);
            LocalDateTime creada = ((java.sql.Timestamp) fila.get("creada_en")).toLocalDateTime();
            LocalDateTime expira = ((java.sql.Timestamp) fila.get("expira_en")).toLocalDateTime();
            assertThat(Duration.between(creada, expira)).isEqualTo(Duration.ofMinutes(10));
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado = 'RESERVADO' AND reserva_id = '" + id + "'"))
                    .isEqualTo(2);
            assertThat(contar("SELECT count(*) FROM reserva_item WHERE reserva_id = '" + id + "' AND asiento_id IS NOT NULL"))
                    .isEqualTo(2);
        }

        @Test
        void unAsientoOcupadoResponde409ConElAsientoYNoReservaNinguno() throws Exception {
            reservar("200", asientos(3, 11)).andExpect(status().isCreated());

            reservar("101", """
                    {"eventoId": 42, "sectorId": 11, "asientos": [
                      {"fila": 3, "numero": 10}, {"fila": 3, "numero": 11}, {"fila": 3, "numero": 12}]}""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ASIENTO_NO_DISPONIBLE"))
                    .andExpect(jsonPath("$.details[0]").value("F3-N11"))
                    .andExpect(jsonPath("$.details.length()").value(1));

            assertThat(contar("SELECT count(*) FROM asiento WHERE estado = 'RESERVADO'")).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM reserva WHERE cliente_id = '101'")).isZero();
        }

        @Test
        void reservaPorCantidadDescuentaElCupo() throws Exception {
            reservar("101", cantidad(3)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.items.length()").value(0));

            assertThat(disponiblesCampo()).isEqualTo(7);
        }

        @Test
        void cupoInsuficienteResponde409SinCambiarElCupo() throws Exception {
            reservar("200", cantidad(4)).andExpect(status().isCreated());
            reservar("201", cantidad(3)).andExpect(status().isCreated());

            reservar("101", cantidad(4))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CUPO_INSUFICIENTE"));
            assertThat(disponiblesCampo()).isEqualTo(3);
        }

        @Test
        void pedidosInvalidosResponden400() throws Exception {
            reservar("101", """
                    {"eventoId": 42, "sectorId": 11, "asientos": [{"fila": 1, "numero": 1}, {"fila": 1, "numero": 1}]}""")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
            reservar("101", """
                    {"eventoId": 42, "sectorId": 11, "cantidad": 2}""")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
            reservar("101", """
                    {"eventoId": 42, "sectorId": 12, "asientos": [{"fila": 1, "numero": 1}]}""")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
            reservar("101", cantidad(0))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
            reservar("101", asientos(9, 99))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem("F9-N99")));

            assertThat(contar("SELECT count(*) FROM reserva")).isZero();
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE'")).isZero();
        }

        @Test
        void eventoSinInventarioResponde404YCerradoResponde409() throws Exception {
            reservar("101", """
                    {"eventoId": 999, "sectorId": 11, "cantidad": 1}""")
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));

            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());
            reservar("101", cantidad(1))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENTO_NO_DISPONIBLE"));
        }

        @Test
        void superarElLimiteResponde422SinReservarNada() throws Exception {
            reservar("101", cantidad(3)).andExpect(status().isCreated());

            reservar("101", """
                    {"eventoId": 42, "sectorId": 11, "asientos": [{"fila": 1, "numero": 1}, {"fila": 1, "numero": 2}]}""")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("LIMITE_POR_CLIENTE"));
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE'")).isZero();
        }

        @Test
        void reservaVencidaNoCuentaParaElLimite() throws Exception {
            UUID id = idDe(reservar("101", cantidad(4)).andExpect(status().isCreated()));
            expirar(id);

            reservar("101", cantidad(4)).andExpect(status().isCreated());
        }

        @Test
        void rolDistintoDeClienteResponde403YSinHeadersResponde400() throws Exception {
            mvc.perform(post("/reservas").header("X-User-Id", "7").header("X-User-Rol", "ORGANIZADOR")
                            .contentType(MediaType.APPLICATION_JSON).content(cantidad(1)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
            mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(cantidad(1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
            assertThat(disponiblesCampo()).isEqualTo(10);
        }
    }

    @Nested
    class ConsultaYCancelacion {

        @Test
        void elDuenoConsultaSuReservaYOtroClienteRecibe403() throws Exception {
            UUID id = idDe(reservar("101", asientos(2, 5)).andExpect(status().isCreated()));

            mvc.perform(get("/reservas/" + id).header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.items[0].fila").value(2))
                    .andExpect(jsonPath("$.items[0].numero").value(5));
            mvc.perform(get("/reservas/" + id).header("X-User-Id", "202").header("X-User-Rol", "CLIENTE"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/reservas/" + UUID.randomUUID()).header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void cancelarDosVecesDevuelveLosLugaresUnaSolaVez() throws Exception {
            UUID id = idDe(reservar("101", cantidad(3)).andExpect(status().isCreated()));

            cancelar("101", id).andExpect(status().isNoContent());
            cancelar("101", id).andExpect(status().isNoContent());

            assertThat(estado(id)).isEqualTo("LIBERADA");
            assertThat(disponiblesCampo()).isEqualTo(10);
        }

        @Test
        void cancelarAsientosLosDejaLibres() throws Exception {
            UUID id = idDe(reservar("101", asientos(1, 1)).andExpect(status().isCreated()));

            cancelar("101", id).andExpect(status().isNoContent());

            assertThat(contar("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE' OR reserva_id IS NOT NULL")).isZero();
        }

        @Test
        void cancelarConfirmadaResponde409YAjenaResponde403() throws Exception {
            UUID id = idDe(reservar("101", cantidad(1)).andExpect(status().isCreated()));
            confirmar(id).andExpect(status().isOk());

            cancelar("202", id).andExpect(status().isForbidden());
            cancelar("101", id).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESERVA_CONFIRMADA"));
            cancelar("101", UUID.randomUUID()).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Internos {

        @Test
        void confirmarVendeLosAsientosYRepetirResponde200() throws Exception {
            UUID id = idDe(reservar("101", asientos(4, 4)).andExpect(status().isCreated()));

            confirmar(id).andExpect(status().isOk());
            confirmar(id).andExpect(status().isOk());

            assertThat(estado(id)).isEqualTo("CONFIRMADA");
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado = 'VENDIDO'")).isEqualTo(1);
        }

        @Test
        void confirmarVencidaResponde410LiberadaResponde409EInexistente404() throws Exception {
            UUID vencida = idDe(reservar("101", cantidad(1)).andExpect(status().isCreated()));
            expirar(vencida);
            confirmar(vencida).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("RESERVA_VENCIDA"));

            UUID liberada = idDe(reservar("102", cantidad(1)).andExpect(status().isCreated()));
            mvc.perform(post("/internal/reservas/" + liberada + "/liberar")).andExpect(status().isOk());
            confirmar(liberada).andExpect(status().isConflict());

            confirmar(UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        void liberarEsIdempotenteYSobreConfirmadaResponde409() throws Exception {
            UUID id = idDe(reservar("101", cantidad(2)).andExpect(status().isCreated()));

            mvc.perform(post("/internal/reservas/" + id + "/liberar")).andExpect(status().isOk());
            mvc.perform(post("/internal/reservas/" + id + "/liberar")).andExpect(status().isOk());
            assertThat(disponiblesCampo()).isEqualTo(10);

            UUID confirmada = idDe(reservar("102", cantidad(1)).andExpect(status().isCreated()));
            confirmar(confirmada).andExpect(status().isOk());
            mvc.perform(post("/internal/reservas/" + confirmada + "/liberar")).andExpect(status().isConflict());
            mvc.perform(post("/internal/reservas/" + UUID.randomUUID() + "/liberar")).andExpect(status().isNotFound());
        }

        @Test
        void devolverAsientoLoDejaLibreYDejaDeContarParaElLimite() throws Exception {
            UUID id = idDe(reservar("101", """
                    {"eventoId": 42, "sectorId": 11, "asientos": [
                      {"fila": 1, "numero": 1}, {"fila": 1, "numero": 2}, {"fila": 1, "numero": 3}, {"fila": 1, "numero": 4}]}""")
                    .andExpect(status().isCreated()));
            confirmar(id).andExpect(status().isOk());

            devolver(id, """
                    {"items": [{"fila": 1, "numero": 2}]}""").andExpect(status().isOk());
            devolver(id, """
                    {"items": [{"fila": 1, "numero": 2}]}""").andExpect(status().isOk());

            assertThat(contar("SELECT count(*) FROM asiento WHERE fila = 1 AND numero = 2 AND estado = 'LIBRE' "
                    + "AND reserva_id IS NULL")).isEqualTo(1);
            assertThat(contar("SELECT cantidad FROM reserva WHERE id = '" + id + "'")).isEqualTo(3);
            mvc.perform(get("/reservas/" + id).header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                    .andExpect(jsonPath("$.items.length()").value(3));
            reservar("101", asientos(1, 2)).andExpect(status().isCreated());
        }

        @Test
        void devolverAsientoAjenoResponde409YReservaPendienteTambien() throws Exception {
            UUID id = idDe(reservar("101", asientos(1, 1)).andExpect(status().isCreated()));
            devolver(id, """
                    {"items": [{"fila": 1, "numero": 1}]}""")
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ESTADO_INVALIDO"));

            confirmar(id).andExpect(status().isOk());
            devolver(id, """
                    {"items": [{"fila": 1, "numero": 1}, {"fila": 2, "numero": 2}]}""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.details[0]").value("F2-N2"));
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado = 'VENDIDO'")).isEqualTo(1);

            devolver(UUID.randomUUID(), """
                    {"cantidad": 1}""").andExpect(status().isNotFound());
        }

        @Test
        void devolverCuposSumaAlCupoYDescuentaDeLaReservaSinSuperarLoComprado() throws Exception {
            UUID id = idDe(reservar("101", cantidad(3)).andExpect(status().isCreated()));
            confirmar(id).andExpect(status().isOk());

            devolver(id, """
                    {"cantidad": 2}""").andExpect(status().isOk());
            assertThat(disponiblesCampo()).isEqualTo(9);
            assertThat(contar("SELECT cantidad FROM reserva WHERE id = '" + id + "'")).isEqualTo(1);

            devolver(id, """
                    {"cantidad": 2}""").andExpect(status().isConflict());
            assertThat(disponiblesCampo()).isEqualTo(9);
        }
    }

    @Nested
    class Vencimiento {

        @Test
        void elJobVenceLasPendientesExpiradasYDevuelveAsientosYCupo() throws Exception {
            UUID numerada = idDe(reservar("101", asientos(5, 5)).andExpect(status().isCreated()));
            UUID campo = idDe(reservar("102", cantidad(3)).andExpect(status().isCreated()));
            UUID vigente = idDe(reservar("103", cantidad(1)).andExpect(status().isCreated()));
            UUID confirmada = idDe(reservar("104", cantidad(2)).andExpect(status().isCreated()));
            confirmar(confirmada).andExpect(status().isOk());
            expirar(numerada);
            expirar(campo);
            expirar(confirmada);

            vencimientoJob.ejecutar();

            assertThat(estado(numerada)).isEqualTo("VENCIDA");
            assertThat(estado(campo)).isEqualTo("VENCIDA");
            assertThat(estado(vigente)).isEqualTo("PENDIENTE");
            assertThat(estado(confirmada)).isEqualTo("CONFIRMADA");
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE'")).isZero();
            assertThat(disponiblesCampo()).isEqualTo(7);

            vencimientoJob.ejecutar();
            assertThat(disponiblesCampo()).isEqualTo(7);
        }
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions reservar(String clienteId, String body) throws Exception {
        return mvc.perform(post("/reservas").header("X-User-Id", clienteId).header("X-User-Rol", "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions cancelar(String clienteId, UUID id) throws Exception {
        return mvc.perform(delete("/reservas/" + id).header("X-User-Id", clienteId).header("X-User-Rol", "CLIENTE"));
    }

    private ResultActions confirmar(UUID id) throws Exception {
        return mvc.perform(post("/internal/reservas/" + id + "/confirmar"));
    }

    private ResultActions devolver(UUID id, String body) throws Exception {
        return mvc.perform(post("/internal/reservas/" + id + "/devolver")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String asientos(int fila, int numero) {
        return "{\"eventoId\": 42, \"sectorId\": 11, \"asientos\": [{\"fila\": " + fila + ", \"numero\": " + numero + "}]}";
    }

    private static String cantidad(int cantidad) {
        return "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": " + cantidad + "}";
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private UUID idDe(ResultActions resultado) throws Exception {
        return UUID.fromString(leer(resultado).get("reservaId").asText());
    }

    private void expirar(UUID id) {
        jdbc.update("UPDATE reserva SET expira_en = localtimestamp - interval '1 minute' WHERE id = ?", id);
    }

    private String estado(UUID id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    private int disponiblesCampo() {
        return contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12");
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }
}
