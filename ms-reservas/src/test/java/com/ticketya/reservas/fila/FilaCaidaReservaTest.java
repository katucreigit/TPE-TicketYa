package com.ticketya.reservas.fila;

import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import com.ticketya.reservas.trazabilidad.CapturaLogs;
import com.ticketya.reservas.trazabilidad.Correlacion;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario "ms-fila detenido": la URL de ms-fila apunta a un puerto local que no acepta conexiones (se reserva un
 * puerto libre y se cierra). La reserva sigue (fail-open) y queda la advertencia con el correlationId.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FilaCaidaReservaTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;
    private static final int PUERTO_CERRADO = puertoLibre();

    @DynamicPropertySource
    static void apuntarAUnPuertoCerrado(DynamicPropertyRegistry registro) {
        registro.add("ticketya.fila.consultar", () -> "true");
        registro.add("ticketya.services.fila-url", () -> "http://localhost:" + PUERTO_CERRADO);
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
        logs = new CapturaLogs(ReservaService.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
    }

    @Test
    @DisplayName("Reserva sin bloqueo cuando la fila no responde: ms-fila detenido")
    void msFilaDetenido() throws Exception {
        mvc.perform(post("/reservas").header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header(Correlacion.HEADER, "corr-caido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":42,\"sectorId\":11,"
                                + "\"asientos\":[{\"fila\":3,\"numero\":10},{\"fila\":3,\"numero\":11}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        assertThat(e.estadoAsiento(EVENTO, PLATEA, 3, 10)).isEqualTo("RESERVADO");
        assertThat(logs.campos("FILA_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l)
                        .containsEntry("eventoId", "42")
                        .containsEntry("clienteId", "101")
                        .containsEntry("correlationId", "corr-caido")
                        .containsKey("causa"));
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
