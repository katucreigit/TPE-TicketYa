package com.ticketya.ventas.trazabilidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.service.CompraService;
import com.ticketya.ventas.service.PasarelaPagoService;
import com.ticketya.ventas.service.ReconciliacionPagosJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario "Propagacion del identificador de correlacion": ms-ventas llama a ms-eventos, ms-reservas y
 * ms-notificaciones y cada receptor debe recibir el mismo correlationId. Aca los tres clientes HTTP son los reales
 * y cada servicio remoto es un servidor HTTP real en un puerto libre que registra los headers recibidos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PropagacionCorrelacionTest {

    private static final long EVENTO = 42L;
    private static final long SECTOR = 11L;

    /** Un pedido recibido por alguno de los servicios remotos de prueba. */
    record Recibido(String servicio, String metodo, String ruta, String correlationId) {
    }

    private static final List<Recibido> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer MS_EVENTOS = iniciarMsEventos();
    private static final HttpServer MS_RESERVAS = iniciarMsReservas();
    private static final HttpServer MS_NOTIFICACIONES = iniciarMsNotificaciones();

    @DynamicPropertySource
    static void apuntarAServiciosDePrueba(DynamicPropertyRegistry registro) {
        registro.add("ticketya.services.eventos-url",
                () -> "http://localhost:" + MS_EVENTOS.getAddress().getPort());
        registro.add("ticketya.services.reservas-url",
                () -> "http://localhost:" + MS_RESERVAS.getAddress().getPort());
        registro.add("ticketya.services.notificaciones-url",
                () -> "http://localhost:" + MS_NOTIFICACIONES.getAddress().getPort());
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PasarelaPagoService pasarelaPagoService;
    @Autowired
    private ReconciliacionPagosJob reconciliacionPagosJob;

    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE reintegro, devolucion, entrada, pago, compra CASCADE");
        pasarelaPagoService.limpiarSimulaciones();
        RECIBIDOS.clear();
        logs = new CapturaLogs(RegistroOperaciones.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
        MDC.clear();
    }

    @AfterAll
    static void detener() {
        MS_EVENTOS.stop(0);
        MS_RESERVAS.stop(0);
        MS_NOTIFICACIONES.stop(0);
    }

    @Test
    @DisplayName("Propagacion en POST /compras: ms-reservas, ms-eventos y ms-notificaciones reciben el correlationId")
    void laCompraPropagaElCorrelationIdAEventosReservasYNotificaciones() throws Exception {
        UUID reservaId = UUID.randomUUID();

        mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-prop-1")
                        .header(Correlacion.HEADER, "corr-compra-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(reservaId, "tok_ok")))
                .andExpect(status().isCreated());

        assertThat(RECIBIDOS).extracting(Recibido::servicio)
                .contains("ms-reservas", "ms-eventos", "ms-notificaciones");
        assertThat(RECIBIDOS).extracting(r -> r.servicio() + " " + r.metodo() + " " + r.ruta())
                .contains(
                        "ms-reservas GET /reservas/" + reservaId,
                        "ms-eventos GET /eventos/" + EVENTO,
                        "ms-eventos GET /eventos/" + EVENTO + "/sectores",
                        "ms-reservas POST /internal/reservas/" + reservaId + "/confirmar",
                        "ms-notificaciones POST /internal/notificaciones");
        assertThat(RECIBIDOS).allSatisfy(r -> assertThat(r.correlationId()).isEqualTo("corr-compra-99"));
    }

    @Test
    void sinCorrelationIdEntranteSePropagaElGeneradoYEsElMismoDeLaRespuesta() throws Exception {
        UUID reservaId = UUID.randomUUID();

        String generado = mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-prop-gen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(reservaId, "tok_ok")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(Correlacion.HEADER);

        assertThat(generado).isNotBlank();
        assertThat(RECIBIDOS).isNotEmpty()
                .allSatisfy(r -> assertThat(r.correlationId()).isEqualTo(generado));
    }

    @Test
    @DisplayName("Aprobar devolucion propaga el correlationId a ms-reservas (/internal/reservas/{id}/devolver)")
    void aprobarDevolucionPropagaElCorrelationIdAMsReservas() throws Exception {
        UUID reservaId = UUID.randomUUID();
        String bodyCompra = mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-prop-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(reservaId, "tok_ok")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String entradaId = json.readTree(bodyCompra).get("entradas").get(0).get("entradaId").asText();

        String bodyDev = mvc.perform(post("/devoluciones")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entradaId\":\"" + entradaId + "\",\"motivo\":\"Cambio de planes\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String devolucionId = json.readTree(bodyDev).get("devolucionId").asText();
        RECIBIDOS.clear();

        mvc.perform(post("/devoluciones/{id}/aprobar", devolucionId)
                        .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN")
                        .header(Correlacion.HEADER, "corr-dev-aprobar"))
                .andExpect(status().isOk());

        assertThat(RECIBIDOS).singleElement().satisfies(r -> {
            assertThat(r.servicio()).isEqualTo("ms-reservas");
            assertThat(r.metodo()).isEqualTo("POST");
            assertThat(r.ruta()).isEqualTo("/internal/reservas/" + reservaId + "/devolver");
            assertThat(r.correlationId()).isEqualTo("corr-dev-aprobar");
        });
    }

    @Test
    @DisplayName("Cancelacion de evento propaga el correlationId a ms-notificaciones")
    void cancelacionDeEventoPropagaElCorrelationIdAMsNotificaciones() throws Exception {
        UUID reservaId = UUID.randomUUID();
        mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-prop-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(reservaId, "tok_ok")))
                .andExpect(status().isCreated());
        RECIBIDOS.clear();

        mvc.perform(post("/internal/eventos/{e}/cancelacion", EVENTO)
                        .header(Correlacion.HEADER, "corr-cancel-notif"))
                .andExpect(status().isOk());

        assertThat(RECIBIDOS).singleElement().satisfies(r -> {
            assertThat(r.servicio()).isEqualTo("ms-notificaciones");
            assertThat(r.metodo()).isEqualTo("POST");
            assertThat(r.ruta()).isEqualTo("/internal/notificaciones");
            assertThat(r.correlationId()).isEqualTo("corr-cancel-notif");
        });
    }

    @Test
    @DisplayName("ReconciliacionPagosJob propaga su correlationId propio a reservas, eventos y notificaciones")
    void elJobDeReconciliacionPropagaSuCorrelationIdYCoincideConSusRegistros() throws Exception {
        UUID reservaId = UUID.randomUUID();
        mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-prop-job")
                        .header(Correlacion.HEADER, "corr-inicial-timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(reservaId, "tok_timeout")))
                .andExpect(status().isGatewayTimeout());

        String compraId = logs.operaciones("PAGO_INCIERTO").get(0).get("compraId");
        pasarelaPagoService.resolverIncertidumbre(
                CompraService.claveCobroDe(UUID.fromString(compraId)), EstadoPago.APROBADO);
        RECIBIDOS.clear();
        logs.limpiar();

        reconciliacionPagosJob.reconciliarPagosInciertos();

        String delJob = logs.operaciones("PAGO_RECONCILIADO").get(0).get("correlationId");
        assertThat(UUID.fromString(delJob)).isNotNull();
        assertThat(delJob).isNotEqualTo("corr-inicial-timeout");
        assertThat(RECIBIDOS).extracting(Recibido::servicio)
                .contains("ms-reservas", "ms-eventos", "ms-notificaciones");
        assertThat(RECIBIDOS).allSatisfy(r -> assertThat(r.correlationId()).isEqualTo(delJob));
        assertThat(MDC.get(Correlacion.MDC_CORRELATION_ID)).isNull();
    }

    // ------------------------------------------------------------------ servidores de prueba

    private static String cuerpoCompra(UUID reservaId, String token) {
        return """
                {
                  "reservaId": "%s",
                  "medioPago": {"tipo": "TARJETA_CREDITO", "token": "%s", "ultimos4": "4242"}
                }""".formatted(reservaId, token);
    }

    private static HttpServer iniciarMsEventos() {
        return iniciarServidor("ms-eventos", (metodo, ruta) -> {
            if (ruta.endsWith("/sectores")) {
                return new Respuesta(200, "[{\"sectorId\":" + SECTOR + ",\"precio\":45000.00}]");
            }
            return new Respuesta(200,
                    "{\"id\":" + EVENTO + ",\"estado\":\"PUBLICADO\",\"fechaHora\":\"2028-06-15T21:00:00\"}");
        });
    }

    private static HttpServer iniciarMsReservas() {
        return iniciarServidor("ms-reservas", (metodo, ruta) -> {
            if ("GET".equals(metodo) && ruta.startsWith("/reservas/")) {
                String id = ruta.substring("/reservas/".length());
                String json = """
                        {
                          "reservaId": "%s",
                          "eventoId": %d,
                          "sectorId": %d,
                          "clienteId": "101",
                          "estado": "PENDIENTE",
                          "cantidad": 1,
                          "items": [{"fila": 1, "numero": 1}],
                          "expiraEn": "2028-06-15T20:00:00"
                        }""".formatted(id, EVENTO, SECTOR);
                return new Respuesta(200, json);
            }
            return new Respuesta(200, null);
        });
    }

    private static HttpServer iniciarMsNotificaciones() {
        return iniciarServidor("ms-notificaciones", (metodo, ruta) -> new Respuesta(202, null));
    }

    private record Respuesta(int status, String json) {
    }

    @FunctionalInterface
    private interface Manejador {
        Respuesta responder(String metodo, String ruta);
    }

    private static HttpServer iniciarServidor(String nombre, Manejador manejador) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", intercambio -> {
                intercambio.getRequestBody().readAllBytes();
                String metodo = intercambio.getRequestMethod();
                String ruta = intercambio.getRequestURI().getPath();
                RECIBIDOS.add(new Recibido(nombre, metodo, ruta,
                        intercambio.getRequestHeaders().getFirst(Correlacion.HEADER)));
                Respuesta resp = manejador.responder(metodo, ruta);
                if (resp.json() != null) {
                    byte[] bytes = resp.json().getBytes(StandardCharsets.UTF_8);
                    intercambio.getResponseHeaders().set("Content-Type", "application/json");
                    intercambio.sendResponseHeaders(resp.status(), bytes.length);
                    try (OutputStream out = intercambio.getResponseBody()) {
                        out.write(bytes);
                    }
                } else {
                    intercambio.sendResponseHeaders(resp.status(), -1);
                }
                intercambio.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
