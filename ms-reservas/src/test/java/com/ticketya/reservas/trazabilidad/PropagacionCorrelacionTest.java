package com.ticketya.reservas.trazabilidad;

import com.sun.net.httpserver.HttpServer;
import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import com.ticketya.reservas.service.VencimientoJob;
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
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ticketya.reservas.Escenario.noNumerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario "Propagacion del identificador de correlacion": ms-reservas llama a ms-eventos (aviso de agotado) y el
 * receptor debe recibir el mismo correlationId. Aca EventosClient es el real y ms-eventos es un servidor HTTP real
 * en un puerto libre que guarda los headers que le llegan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PropagacionCorrelacionTest {

    private static final long EVENTO = 42L;
    private static final long CAMPO = 12L;

    /** Un pedido recibido por el ms-eventos de prueba. */
    record Recibido(String metodo, String ruta, String correlationId, String cuerpo) {
    }

    private static final List<Recibido> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer MS_EVENTOS = iniciarMsEventos();

    @DynamicPropertySource
    static void apuntarAlMsEventosDePrueba(DynamicPropertyRegistry registro) {
        registro.add("ticketya.services.eventos-url",
                () -> "http://localhost:" + MS_EVENTOS.getAddress().getPort());
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private VencimientoJob vencimientoJob;

    private Escenario e;
    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 10, noNumerado(CAMPO, 2));
        RECIBIDOS.clear();
        logs = new CapturaLogs(RegistroOperaciones.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
    }

    @AfterAll
    static void detener() {
        MS_EVENTOS.stop(0);
    }

    @Test
    @DisplayName("Propagacion del identificador de correlacion: el aviso de agotado lleva el de la solicitud")
    void elAvisoDeAgotadoLlevaElCorrelationIdDeLaSolicitudQueLoProvoco() throws Exception {
        mvc.perform(post("/reservas").header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header(Correlacion.HEADER, "corr-agotado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 2}"))
                .andExpect(status().isCreated());

        assertThat(RECIBIDOS).singleElement().satisfies(r -> {
            assertThat(r.metodo()).isEqualTo("PUT");
            assertThat(r.ruta()).isEqualTo("/internal/eventos/42/disponibilidad");
            assertThat(r.cuerpo()).contains("\"agotado\":true");
            assertThat(r.correlationId()).isEqualTo("corr-agotado");
        });
    }

    @Test
    void sinCorrelationIdEntranteSePropagaElGeneradoYEsElMismoDeLaRespuesta() throws Exception {
        String generado = mvc.perform(post("/reservas").header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(Correlacion.HEADER);

        assertThat(RECIBIDOS).singleElement()
                .satisfies(r -> assertThat(r.correlationId()).isNotBlank().isEqualTo(generado));
    }

    @Test
    void elJobDeVencimientoPropagaSuCorrelationIdYEsElDeSusRegistros() {
        UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 2);  // agota el evento: aviso agotado=true
        e.expirar(id);
        RECIBIDOS.clear();
        logs.limpiar();

        vencimientoJob.ejecutar();  // vence la reserva y avisa agotado=false

        String deLaVencida = logs.operaciones("RESERVA_VENCIDA").get(0).get("correlationId");
        assertThat(RECIBIDOS).singleElement().satisfies(r -> {
            assertThat(r.cuerpo()).contains("\"agotado\":false");
            assertThat(r.correlationId()).isNotBlank().isEqualTo(deLaVencida);
        });
    }

    private static HttpServer iniciarMsEventos() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECIBIDOS.add(new Recibido(intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(),
                        intercambio.getRequestHeaders().getFirst(Correlacion.HEADER), cuerpo.replace(" ", "")));
                intercambio.sendResponseHeaders(204, -1);
                intercambio.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
