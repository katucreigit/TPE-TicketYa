package com.ticketya.reservas.fila;

import com.sun.net.httpserver.HttpServer;
import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import com.ticketya.reservas.trazabilidad.CapturaLogs;
import com.ticketya.reservas.trazabilidad.Correlacion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fila virtual al reservar (spec reservas-temporales, add-fila-virtual). FilaClient es el real y ms-fila es un
 * servidor HTTP en un puerto libre cuya respuesta, codigo y demora se controlan desde cada prueba. La consulta esta
 * activada solo en esta clase (ticketya.fila.consultar=true); el resto de las pruebas la tiene apagada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FilaVirtualReservaTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;

    record Consulta(String ruta, String correlationId) {
    }

    private static final List<Consulta> CONSULTAS = new CopyOnWriteArrayList<>();
    private static volatile int codigo;
    private static volatile String respuesta;
    private static volatile long demoraMs;
    private static final HttpServer MS_FILA = iniciarMsFila();

    @DynamicPropertySource
    static void apuntarAlMsFilaDePrueba(DynamicPropertyRegistry registro) {
        registro.add("ticketya.fila.consultar", () -> "true");
        registro.add("ticketya.services.fila-url", () -> "http://localhost:" + MS_FILA.getAddress().getPort());
    }

    @MockitoBean
    private EventosClient eventosClient;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;

    private Escenario e;
    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 10, numerado(PLATEA, 5, 20));
        CONSULTAS.clear();
        responder(200, "{\"activa\":false,\"habilitado\":true}", 0);
        logs = new CapturaLogs(ReservaService.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
    }

    @AfterAll
    static void detener() {
        MS_FILA.stop(0);
    }

    // ------------------------------------------------------------------ Fila virtual al reservar

    @Test
    @DisplayName("Fila virtual al reservar: Cliente en espera intenta reservar")
    void clienteEnEsperaIntentaReservar() throws Exception {
        responder(200, "{\"activa\":true,\"habilitado\":false}", 0);

        reservarDosAsientos("101", "CLIENTE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILA_NO_HABILITADO"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/reservas"));

        assertThat(e.estadoAsiento(EVENTO, PLATEA, 3, 10)).isEqualTo("LIBRE");
        assertThat(e.estadoAsiento(EVENTO, PLATEA, 3, 11)).isEqualTo("LIBRE");
        assertThat(e.entero("SELECT count(*) FROM reserva")).isZero();
        assertThat(CONSULTAS).singleElement()
                .satisfies(c -> assertThat(c.ruta()).isEqualTo("/internal/fila/eventos/42/clientes/101/estado"));
    }

    @Test
    @DisplayName("Fila virtual al reservar: Cliente habilitado reserva")
    void clienteHabilitadoReserva() throws Exception {
        responder(200, "{\"activa\":true,\"habilitado\":true}", 0);

        reservarDosAsientos("101", "CLIENTE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        assertThat(e.estadoAsiento(EVENTO, PLATEA, 3, 10)).isEqualTo("RESERVADO");
    }

    @Test
    @DisplayName("Fila virtual al reservar: Evento sin fila activa")
    void eventoSinFilaActiva() throws Exception {
        reservarDosAsientos("101", "CLIENTE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        assertThat(CONSULTAS).hasSize(1);
        assertThat(logs.mensajes("FILA_NO_DISPONIBLE")).isEmpty();
    }

    @Test
    @DisplayName("Fila virtual al reservar: Organizador reserva en un evento con fila")
    void organizadorReservaEnEventoConFila() throws Exception {
        responder(200, "{\"activa\":true,\"habilitado\":false}", 0);

        reservarDosAsientos("300", "ORGANIZADOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        assertThat(CONSULTAS).isEmpty();
    }

    @Test
    @DisplayName("Fila virtual al reservar: Pedido inválido en un evento con fila")
    void pedidoInvalidoEnEventoConFila() throws Exception {
        responder(200, "{\"activa\":true,\"habilitado\":false}", 0);

        reservar("101", "CLIENTE", "{\"eventoId\":42,\"sectorId\":11,"
                + "\"asientos\":[{\"fila\":3,\"numero\":10},{\"fila\":3,\"numero\":10}]}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        assertThat(CONSULTAS).isEmpty();
    }

    // ------------------------------------------------------------------ Reserva sin bloqueo cuando la fila no responde

    @Test
    @DisplayName("Reserva sin bloqueo cuando la fila no responde: ms-fila lento")
    void msFilaLento() throws Exception {
        responder(200, "{\"activa\":true,\"habilitado\":false}", 5000);

        long inicio = System.nanoTime();
        reservarDosAsientos("101", "CLIENTE").andExpect(status().isCreated());
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(duracionMs).isLessThan(2000);
        assertThat(logs.campos("FILA_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("eventoId", "42").containsEntry("clienteId", "101"));
    }

    @Test
    @DisplayName("Reserva sin bloqueo cuando la fila no responde: ms-fila responde con error")
    void msFilaRespondeConError() throws Exception {
        responder(503, "{\"code\":\"SERVICIO_NO_DISPONIBLE\"}", 0);

        reservarDosAsientos("101", "CLIENTE", "corr-503")
                .andExpect(status().isCreated());

        assertThat(logs.campos("FILA_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l)
                        .containsEntry("eventoId", "42")
                        .containsEntry("clienteId", "101")
                        .containsEntry("correlationId", "corr-503"));
    }

    @Test
    @DisplayName("Reserva sin bloqueo cuando la fila no responde: respuesta ilegible")
    void msFilaRespondeIlegible() throws Exception {
        responder(200, "esto no es json", 0);

        reservarDosAsientos("101", "CLIENTE").andExpect(status().isCreated());

        assertThat(logs.mensajes("FILA_NO_DISPONIBLE")).hasSize(1);
    }

    // ------------------------------------------------------------------ Correlación en la consulta a la fila

    @Test
    @DisplayName("Correlación en la consulta a la fila: Propagación del identificador a ms-fila")
    void propagacionDelIdentificador() throws Exception {
        reservarDosAsientos("101", "CLIENTE", "abc-123").andExpect(status().isCreated());

        assertThat(CONSULTAS).singleElement()
                .satisfies(c -> assertThat(c.correlationId()).isEqualTo("abc-123"));
    }

    // ------------------------------------------------------------------ utilidades

    private ResultActions reservarDosAsientos(String userId, String rol) throws Exception {
        return reservarDosAsientos(userId, rol, null);
    }

    private ResultActions reservarDosAsientos(String userId, String rol, String correlationId) throws Exception {
        return reservar(userId, rol, "{\"eventoId\":42,\"sectorId\":11,"
                + "\"asientos\":[{\"fila\":3,\"numero\":10},{\"fila\":3,\"numero\":11}]}", correlationId);
    }

    private ResultActions reservar(String userId, String rol, String cuerpo, String correlationId) throws Exception {
        var req = post("/reservas").header("X-User-Id", userId).header("X-User-Rol", rol)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (correlationId != null) {
            req.header(Correlacion.HEADER, correlationId);
        }
        return mvc.perform(req);
    }

    private static void responder(int status, String cuerpo, long demora) {
        codigo = status;
        respuesta = cuerpo;
        demoraMs = demora;
    }

    private static HttpServer iniciarMsFila() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            // Varios hilos: una respuesta demorada no frena las consultas de las pruebas siguientes.
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", intercambio -> {
                CONSULTAS.add(new Consulta(intercambio.getRequestURI().getPath(),
                        intercambio.getRequestHeaders().getFirst(Correlacion.HEADER)));
                try {
                    if (demoraMs > 0) {
                        Thread.sleep(demoraMs);
                    }
                    byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
                    intercambio.getResponseHeaders().add("Content-Type", "application/json");
                    intercambio.sendResponseHeaders(codigo, cuerpo.length);
                    intercambio.getResponseBody().write(cuerpo);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (IOException ex) {
                    // El cliente corto por timeout: no hay a quien responder.
                } finally {
                    intercambio.close();
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
