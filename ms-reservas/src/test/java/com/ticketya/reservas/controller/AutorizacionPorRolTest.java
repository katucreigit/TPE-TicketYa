package com.ticketya.reservas.controller;

import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static com.ticketya.reservas.Escenario.noNumerado;
import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * add-jwt-autorizacion (spec user-auth, tareas 2.2 a 2.4) aplicada a ms-reservas, segun docs/openapi.yaml y
 * docs/user-stories.md: POST /reservas y GET/DELETE /reservas/{id} son solo del rol CLIENTE (y en las dos ultimas,
 * solo del cliente duenio); la disponibilidad y el mapa de asientos son publicos. El resto responde 403.
 * Roles oficiales: CLIENTE, ORGANIZADOR, ADMIN y PERSONAL_ACCESO. Los /internal/** no se tocan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AutorizacionPorRolTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;
    private static final long CAMPO = 12L;
    private static final String CUERPO_RESERVA =
            "{\"eventoId\": 42, \"sectorId\": 11, \"asientos\": [{\"fila\": 1, \"numero\": 1}]}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;

    @MockitoBean
    private EventosClient eventosClient;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 10, numerado(PLATEA, 3, 10), noNumerado(CAMPO, 20));
    }

    @Nested
    @DisplayName("Rol autorizado")
    class RolAutorizado {

        @Test
        @DisplayName("CLIENTE puede reservar, consultar y cancelar su reserva")
        void elClientePuedeReservarConsultarYCancelar() throws Exception {
            String body = mvc.perform(como(post("/reservas"), "101", "CLIENTE")
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_RESERVA))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String id = body.replaceAll(".*\"reservaId\":\"([^\"]+)\".*", "$1");

            mvc.perform(como(get("/reservas/{id}", id), "101", "CLIENTE")).andExpect(status().isOk());
            mvc.perform(como(delete("/reservas/{id}", id), "101", "CLIENTE")).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Identificacion correcta: la reserva queda a nombre del X-User-Id recibido")
        void laReservaQuedaANombreDelUsuarioIdentificado() throws Exception {
            mvc.perform(como(post("/reservas"), "  205 ", "CLIENTE")
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_RESERVA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clienteId").value("205"));

            assertThat(e.entero("SELECT count(*) FROM reserva WHERE cliente_id = '205'")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Rol no autorizado")
    class RolNoAutorizado {

        @ParameterizedTest
        @ValueSource(strings = {"ORGANIZADOR", "ADMIN", "PERSONAL_ACCESO"})
        @DisplayName("POST /reservas con un rol distinto de CLIENTE responde 403 y no reserva nada")
        void reservarConOtroRolEs403SinReservar(String rol) throws Exception {
            mvc.perform(como(post("/reservas"), "101", rol)
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_RESERVA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

            assertThat(e.entero("SELECT count(*) FROM reserva")).isZero();
            assertThat(e.estadoAsiento(EVENTO, PLATEA, 1, 1)).isEqualTo("LIBRE");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ORGANIZADOR", "ADMIN", "PERSONAL_ACCESO"})
        @DisplayName("GET /reservas/{id} con un rol distinto de CLIENTE responde 403, aunque el id sea el del duenio")
        void consultarConOtroRolEs403(String rol) throws Exception {
            UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 1, 2);

            mvc.perform(como(get("/reservas/{id}", id), "101", rol))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ORGANIZADOR", "ADMIN", "PERSONAL_ACCESO"})
        @DisplayName("DELETE /reservas/{id} con un rol distinto de CLIENTE responde 403 y no libera nada")
        void cancelarConOtroRolEs403SinLiberar(String rol) throws Exception {
            UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 3);

            mvc.perform(como(delete("/reservas/{id}", id), "101", rol))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

            assertThat(e.estado(id)).isEqualTo("PENDIENTE");
            assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(17);
        }

        @ParameterizedTest
        @ValueSource(strings = {"cliente", "Cliente", "SISTEMA", "DESCONOCIDO"})
        void unRolQueNoEsExactamenteClienteEs403(String rol) throws Exception {
            mvc.perform(como(post("/reservas"), "101", rol)
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_RESERVA))
                    .andExpect(status().isForbidden());

            assertThat(e.entero("SELECT count(*) FROM reserva")).isZero();
        }
    }

    @Nested
    @DisplayName("Propagacion segura de identidad")
    class PropagacionSegura {

        @Test
        @DisplayName("Intento de suplantacion: otro CLIENTE no puede ver ni cancelar una reserva ajena")
        void otroClienteNoPuedeVerNiCancelarUnaReservaAjena() throws Exception {
            UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 2, 2);

            mvc.perform(como(get("/reservas/{id}", id), "202", "CLIENTE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
            mvc.perform(como(delete("/reservas/{id}", id), "202", "CLIENTE"))
                    .andExpect(status().isForbidden());

            assertThat(e.estado(id)).isEqualTo("PENDIENTE");
            assertThat(e.estadoAsiento(EVENTO, PLATEA, 2, 2)).isEqualTo("RESERVADO");
        }

        @Test
        void sinHeadersDeIdentidadResponde400YNoReserva() throws Exception {
            mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_RESERVA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
            mvc.perform(post("/reservas").header("X-User-Rol", "CLIENTE")
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_RESERVA))
                    .andExpect(status().isBadRequest());

            assertThat(e.entero("SELECT count(*) FROM reserva")).isZero();
        }
    }

    @Nested
    @DisplayName("Endpoints publicos")
    class EndpointsPublicos {

        @ParameterizedTest
        @ValueSource(strings = {"CLIENTE", "ORGANIZADOR", "ADMIN", "PERSONAL_ACCESO"})
        @DisplayName("La disponibilidad y el mapa de asientos responden 200 con cualquier rol")
        void disponibilidadYAsientosConCualquierRol(String rol) throws Exception {
            mvc.perform(como(get("/eventos/{e}/disponibilidad", EVENTO), "9", rol)).andExpect(status().isOk());
            mvc.perform(como(get("/eventos/{e}/sectores/{s}/asientos", EVENTO, PLATEA), "9", rol))
                    .andExpect(status().isOk());
        }

        @Test
        void disponibilidadYAsientosSinIdentidad() throws Exception {
            mvc.perform(get("/eventos/{e}/disponibilidad", EVENTO)).andExpect(status().isOk());
            mvc.perform(get("/eventos/{e}/sectores/{s}/asientos", EVENTO, PLATEA)).andExpect(status().isOk());
        }
    }

    private static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder req, String userId, String rol) {
        return req.header("X-User-Id", userId).header("X-User-Rol", rol);
    }
}
