package com.ticketya.reservas.trazabilidad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import com.ticketya.reservas.service.VencimientoJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ticketya.reservas.Escenario.noNumerado;
import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * add-trazabilidad (spec trazabilidad) aplicada a ms-reservas: correlationId, registro de cada solicitud y registro
 * de operaciones criticas. HTTP real (MockMvc), servicio real y PostgreSQL real; solo se reemplaza el cliente a
 * ms-eventos. La propagacion saliente se prueba aparte, contra un servidor HTTP real, en PropagacionCorrelacionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TrazabilidadTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;
    private static final long CAMPO = 12L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private VencimientoJob vencimientoJob;
    @Autowired
    private RegistroOperaciones registro;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EventosClient eventosClient;

    private Escenario e;
    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 10, numerado(PLATEA, 5, 20), noNumerado(CAMPO, 50));
        logs = new CapturaLogs(RegistroSolicitudesFilter.class, RegistroOperaciones.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
    }

    // ------------------------------------------------------------------ identificador de correlacion

    @Nested
    @DisplayName("Identificador de correlacion compartido")
    class IdentificadorDeCorrelacion {

        @Test
        @DisplayName("Solicitud sin identificador de correlacion: se genera uno y relaciona sus registros")
        void sinCorrelationIdSeGeneraUnoYRelacionaLosRegistros() throws Exception {
            String generado = reservar("101", null, PLATEA, 1, 1)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getHeader(Correlacion.HEADER);

            assertThat(generado).isNotBlank();
            assertThat(UUID.fromString(generado)).isNotNull();
            assertThat(logs.campos("HTTP_REQUEST")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo(generado));
            assertThat(logs.operaciones("RESERVA_CREADA")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo(generado));
        }

        @Test
        @DisplayName("Solicitud con identificador de correlacion: se conserva en la respuesta y en los registros")
        void conCorrelationIdSeConservaEnLaRespuestaYEnLosRegistros() throws Exception {
            reservar("101", "compra-abc-123", PLATEA, 1, 2)
                    .andExpect(status().isCreated())
                    .andExpect(header().string(Correlacion.HEADER, "compra-abc-123"));

            assertThat(logs.campos("HTTP_REQUEST")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo("compra-abc-123"));
            assertThat(logs.operaciones("RESERVA_CREADA")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo("compra-abc-123"));
            // El MDC tambien lo tiene: el patron de log lo agrega a cada linea del servicio.
            assertThat(logs.eventos("OPERACION_CRITICA")).singleElement()
                    .satisfies(ev -> assertThat(ev.getMDCPropertyMap())
                            .containsEntry(Correlacion.MDC_CORRELATION_ID, "compra-abc-123"));
        }

        @Test
        void unEndpointInternoTambienConservaElCorrelationIdRecibido() throws Exception {
            UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 1, 3);
            logs.limpiar();

            mvc.perform(post("/internal/reservas/{id}/confirmar", id).header(Correlacion.HEADER, "venta-77"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(Correlacion.HEADER, "venta-77"));

            assertThat(logs.operaciones("RESERVA_CONFIRMADA")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo("venta-77"));
        }

        @Test
        void unCorrelationIdConSaltosDeLineaSeReemplazaPorUnoGenerado() throws Exception {
            String respuesta = mvc.perform(get("/eventos/{e}/disponibilidad", EVENTO)
                            .header(Correlacion.HEADER, "abc\nOPERACION_CRITICA operacion=FALSA"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(Correlacion.HEADER);

            assertThat(UUID.fromString(respuesta)).isNotNull();
            assertThat(logs.mensajes("OPERACION_CRITICA")).isEmpty();
        }

        @Test
        void elMdcQuedaLimpioAlTerminarLaSolicitud() throws Exception {
            mvc.perform(get("/reservas/{id}", UUID.randomUUID())
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .header(Correlacion.HEADER, "corr-mdc"))
                    .andExpect(status().isNotFound());

            assertThat(MDC.get(Correlacion.MDC_CORRELATION_ID)).isNull();
            assertThat(MDC.get(Correlacion.MDC_USUARIO)).isNull();
            assertThat(MDC.get(Correlacion.MDC_ROL)).isNull();
        }
    }

    // ------------------------------------------------------------------ registro de solicitudes

    @Nested
    @DisplayName("Registro de solicitudes HTTP")
    class RegistroDeSolicitudes {

        @Test
        @DisplayName("Registro de una solicitud HTTP: metodo, ruta, usuario, rol, codigo, tiempo y correlationId")
        void registraMetodoRutaUsuarioRolCodigoTiempoYCorrelationId() throws Exception {
            reservar("101", "corr-http-1", PLATEA, 2, 1).andExpect(status().isCreated());

            assertThat(logs.campos("HTTP_REQUEST")).singleElement().satisfies(c -> {
                assertThat(c).containsEntry("method", "POST")
                        .containsEntry("endpoint", "/reservas")
                        .containsEntry("user", "101")
                        .containsEntry("rol", "CLIENTE")
                        .containsEntry("status", "201")
                        .containsEntry("correlationId", "corr-http-1");
                assertThat(Long.parseLong(c.get("durationMs"))).isNotNegative();
            });
        }

        @Test
        void registraLasSolicitudesAnonimasConElCodigoDeRespuesta() throws Exception {
            mvc.perform(get("/eventos/{e}/disponibilidad", EVENTO)).andExpect(status().isOk());

            assertThat(logs.campos("HTTP_REQUEST")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("method", "GET")
                    .containsEntry("endpoint", "/eventos/42/disponibilidad")
                    .containsEntry("user", "ANONIMO")
                    .containsEntry("rol", "ANONIMO")
                    .containsEntry("status", "200"));
        }

        @Test
        void registraElCodigoDeLasRespuestasDeError() throws Exception {
            mvc.perform(post("/reservas").header("X-User-Id", "7").header("X-User-Rol", "ORGANIZADOR")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpoAsientos(PLATEA, 1, 1)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/reservas/{id}", UUID.randomUUID()).header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                    .andExpect(status().isNotFound());

            assertThat(logs.campos("HTTP_REQUEST")).extracting(c -> c.get("status") + " " + c.get("user") + " " + c.get("rol"))
                    .containsExactly("403 7 ORGANIZADOR", "404 101 CLIENTE");
        }
    }

    // ------------------------------------------------------------------ operaciones criticas

    @Nested
    @DisplayName("Registro centralizado de operaciones criticas")
    class OperacionesCriticas {

        @Test
        @DisplayName("Reserva creada: correlationId, usuario e ids de negocio")
        void reservaCreada() throws Exception {
            String body = reservar("101", "corr-crear", PLATEA, 3, 4)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String reservaId = json.readTree(body).get("reservaId").asText();

            assertThat(logs.operaciones("RESERVA_CREADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-crear")
                    .containsEntry("reservaId", reservaId)
                    .containsEntry("eventoId", "42")
                    .containsEntry("sectorId", "11")
                    .containsEntry("clienteId", "101")
                    .containsEntry("cantidad", "1")
                    .containsEntry("asientos", "F3-N4")
                    .containsKey("expiraEn"));
        }

        @Test
        void unaReservaQueFallaNoQuedaRegistradaComoCreada() throws Exception {
            e.reservarAsientos("200", EVENTO, PLATEA, 3, 5);
            logs.limpiar();

            reservar("101", "corr-conflicto", PLATEA, 3, 5).andExpect(status().isConflict());

            assertThat(logs.operaciones("RESERVA_CREADA")).isEmpty();
        }

        @Test
        @DisplayName("Reserva confirmada (ms-ventas): sin X-User-Id el usuario es SISTEMA")
        void reservaConfirmada() throws Exception {
            UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 2);
            logs.limpiar();

            mvc.perform(post("/internal/reservas/{id}/confirmar", id).header(Correlacion.HEADER, "corr-confirmar"))
                    .andExpect(status().isOk());
            // Una confirmacion repetida no cambia nada y no se registra otra vez.
            mvc.perform(post("/internal/reservas/{id}/confirmar", id)).andExpect(status().isOk());

            assertThat(logs.operaciones("RESERVA_CONFIRMADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", Correlacion.SISTEMA)
                    .containsEntry("correlationId", "corr-confirmar")
                    .containsEntry("reservaId", id.toString())
                    .containsEntry("eventoId", "42")
                    .containsEntry("sectorId", "12")
                    .containsEntry("clienteId", "101")
                    .containsEntry("cantidad", "2"));
        }

        @Test
        void unaConfirmacionRechazadaNoQuedaRegistrada() throws Exception {
            UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 4, 1);
            e.expirar(id);
            logs.limpiar();

            mvc.perform(post("/internal/reservas/{id}/confirmar", id)).andExpect(status().isGone());

            assertThat(logs.operaciones("RESERVA_CONFIRMADA")).isEmpty();
        }

        @Test
        @DisplayName("Reserva vencida: el job usa un correlationId propio por corrida y usuario SISTEMA")
        void reservaVencida() {
            UUID a = e.reservarAsientos("101", EVENTO, PLATEA, 5, 1);
            UUID b = e.reservarCantidad("102", EVENTO, CAMPO, 3);
            e.expirar(a);
            e.expirar(b);
            logs.limpiar();

            vencimientoJob.ejecutar();

            List<Map<String, String>> vencidas = logs.operaciones("RESERVA_VENCIDA");
            assertThat(vencidas).extracting(c -> c.get("reservaId"))
                    .containsExactlyInAnyOrder(a.toString(), b.toString());
            assertThat(vencidas).allSatisfy(c -> assertThat(c)
                    .containsEntry("usuario", Correlacion.SISTEMA)
                    .containsEntry("eventoId", "42"));
            String correlacion = vencidas.get(0).get("correlationId");
            assertThat(UUID.fromString(correlacion)).isNotNull();
            assertThat(vencidas).extracting(c -> c.get("correlationId")).containsOnly(correlacion);
            assertThat(MDC.get(Correlacion.MDC_CORRELATION_ID)).isNull();
        }

        @Test
        @DisplayName("Reserva liberada por el cliente: una sola vez aunque cancele dos veces")
        void reservaLiberadaPorElCliente() throws Exception {
            UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 2, 7);
            logs.limpiar();

            cancelar("101", id, "corr-cancelar").andExpect(status().isNoContent());
            cancelar("101", id, null).andExpect(status().isNoContent());

            assertThat(logs.operaciones("RESERVA_LIBERADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-cancelar")
                    .containsEntry("reservaId", id.toString())
                    .containsEntry("clienteId", "101")
                    .containsEntry("motivo", "CANCELACION_CLIENTE"));
        }

        @Test
        void reservaLiberadaPorCompensacion() throws Exception {
            UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 1);
            logs.limpiar();

            mvc.perform(post("/internal/reservas/{id}/liberar", id).header(Correlacion.HEADER, "corr-comp"))
                    .andExpect(status().isOk());

            assertThat(logs.operaciones("RESERVA_LIBERADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("correlationId", "corr-comp")
                    .containsEntry("reservaId", id.toString())
                    .containsEntry("motivo", "COMPENSACION"));
        }

        @Test
        void reservasLiberadasPorElCierreDelInventario() throws Exception {
            UUID a = e.reservarAsientos("101", EVENTO, PLATEA, 1, 9);
            UUID b = e.reservarCantidad("102", EVENTO, CAMPO, 2);
            logs.limpiar();

            mvc.perform(post("/internal/eventos/{e}/cerrar", EVENTO).header(Correlacion.HEADER, "corr-cierre"))
                    .andExpect(status().isOk());

            assertThat(logs.operaciones("RESERVA_LIBERADA"))
                    .extracting(c -> c.get("reservaId") + " " + c.get("motivo") + " " + c.get("correlationId"))
                    .containsExactlyInAnyOrder(
                            a + " CIERRE_INVENTARIO corr-cierre",
                            b + " CIERRE_INVENTARIO corr-cierre");
        }

        @Test
        @DisplayName("Reserva devuelta: solo los asientos que se devolvieron en esa llamada")
        void reservaDevuelta() throws Exception {
            UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 4, 5, 4, 6);
            reservaService.confirmar(id);
            logs.limpiar();

            devolver(id, "{\"items\": [{\"fila\": 4, \"numero\": 6}]}", "corr-dev").andExpect(status().isOk());
            // Repetir la misma devolucion no libera nada nuevo y no se registra.
            devolver(id, "{\"items\": [{\"fila\": 4, \"numero\": 6}]}", null).andExpect(status().isOk());

            assertThat(logs.operaciones("RESERVA_DEVUELTA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("correlationId", "corr-dev")
                    .containsEntry("reservaId", id.toString())
                    .containsEntry("clienteId", "101")
                    .containsEntry("cantidad", "1")
                    .containsEntry("asientos", "F4-N6"));
        }

        @Test
        void reservaDevueltaEnUnSectorNoNumerado() throws Exception {
            UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 3);
            reservaService.confirmar(id);
            logs.limpiar();

            devolver(id, "{\"cantidad\": 2}", "corr-dev-cupo").andExpect(status().isOk());

            assertThat(logs.operaciones("RESERVA_DEVUELTA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("cantidad", "2")
                    .doesNotContainKey("asientos"));
        }

        @Test
        void unaOperacionDentroDeUnaTransaccionRevertidaNoQuedaRegistrada() {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(s -> {
                registro.registrar(RegistroOperaciones.Operacion.RESERVA_CREADA,
                        RegistroOperaciones.datos("reservaId", "revertida"));
                s.setRollbackOnly();
            });
            tx.executeWithoutResult(s -> registro.registrar(RegistroOperaciones.Operacion.RESERVA_CREADA,
                    RegistroOperaciones.datos("reservaId", "confirmada")));

            assertThat(logs.operaciones("RESERVA_CREADA")).extracting(c -> c.get("reservaId"))
                    .containsExactly("confirmada");
        }
    }

    // ------------------------------------------------------------------ seguimiento de punta a punta

    @Test
    @DisplayName("Seguimiento de una compra: reserva, consulta y confirmacion comparten un unico correlationId")
    void seguimientoDeUnaCompraConUnUnicoCorrelationId() throws Exception {
        String corr = "compra-punta-a-punta";
        String body = reservar("101", corr, PLATEA, 5, 5).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("reservaId").asText();
        // ms-ventas consulta la reserva y la confirma reenviando el mismo correlationId.
        mvc.perform(get("/reservas/{id}", id).header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                .header(Correlacion.HEADER, corr)).andExpect(status().isOk());
        mvc.perform(post("/internal/reservas/{id}/confirmar", id).header(Correlacion.HEADER, corr))
                .andExpect(status().isOk());

        assertThat(logs.campos("HTTP_REQUEST")).hasSize(3)
                .allSatisfy(c -> assertThat(c).containsEntry("correlationId", corr));
        assertThat(logs.campos("OPERACION_CRITICA"))
                .extracting(c -> c.get("operacion") + " " + c.get("reservaId") + " " + c.get("correlationId"))
                .containsExactly("RESERVA_CREADA " + id + " " + corr, "RESERVA_CONFIRMADA " + id + " " + corr);
    }

    // ------------------------------------------------------------------ auxiliares

    private ResultActions reservar(String clienteId, String correlationId, long sectorId, int fila, int numero)
            throws Exception {
        var req = post("/reservas").header("X-User-Id", clienteId).header("X-User-Rol", "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoAsientos(sectorId, fila, numero));
        if (correlationId != null) {
            req.header(Correlacion.HEADER, correlationId);
        }
        return mvc.perform(req);
    }

    private ResultActions cancelar(String clienteId, UUID id, String correlationId) throws Exception {
        var req = delete("/reservas/{id}", id).header("X-User-Id", clienteId).header("X-User-Rol", "CLIENTE");
        if (correlationId != null) {
            req.header(Correlacion.HEADER, correlationId);
        }
        return mvc.perform(req);
    }

    private ResultActions devolver(UUID id, String cuerpo, String correlationId) throws Exception {
        var req = post("/internal/reservas/{id}/devolver", id).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (correlationId != null) {
            req.header(Correlacion.HEADER, correlationId);
        }
        return mvc.perform(req);
    }

    private static String cuerpoAsientos(long sectorId, int fila, int numero) {
        return "{\"eventoId\": " + EVENTO + ", \"sectorId\": " + sectorId
                + ", \"asientos\": [{\"fila\": " + fila + ", \"numero\": " + numero + "}]}";
    }
}
