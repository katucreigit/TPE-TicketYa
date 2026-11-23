package com.ticketya.ventas.recomendaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.client.RecomendacionesClient;
import com.ticketya.ventas.service.PasarelaPagoService;
import com.ticketya.ventas.trazabilidad.CapturaLogs;
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
import java.net.ServerSocket;
import java.util.UUID;

import static com.ticketya.ventas.recomendaciones.ServiciosSimulados.cuerpoCompra;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario "ms-recomendaciones caído": la URL apunta a un puerto local sin servicio (se reserva uno libre y se
 * cierra). La compra se aprueba igual y queda la advertencia con el compraId y el correlationId.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AvisoRecomendacionesCaidoTest {

    private static final int PUERTO_CERRADO = puertoLibre();

    @DynamicPropertySource
    static void apuntarAUnPuertoCerrado(DynamicPropertyRegistry registro) {
        registro.add("ticketya.recomendaciones.avisar", () -> "true");
        registro.add("ticketya.services.eventos-url", () -> ServiciosSimulados.url(ServiciosSimulados.MS_EVENTOS));
        registro.add("ticketya.services.reservas-url", () -> ServiciosSimulados.url(ServiciosSimulados.MS_RESERVAS));
        registro.add("ticketya.services.notificaciones-url",
                () -> ServiciosSimulados.url(ServiciosSimulados.MS_NOTIFICACIONES));
        registro.add("ticketya.services.recomendaciones-url", () -> "http://localhost:" + PUERTO_CERRADO);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PasarelaPagoService pasarelaPagoService;

    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE reintegro, devolucion, entrada, pago, compra CASCADE");
        pasarelaPagoService.limpiarSimulaciones();
        logs = new CapturaLogs(RecomendacionesClient.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
    }

    @Test
    @DisplayName("Aviso de compra desde ms-ventas: ms-recomendaciones caído")
    void msRecomendacionesCaido() throws Exception {
        String body = mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-rec-caido")
                        .header("X-Correlation-Id", "corr-caido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(UUID.randomUUID(), "tok_ok")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode compra = json.readTree(body);

        assertThat(compra.get("estado").asText()).isEqualTo("APROBADA");
        assertThat(logs.campos("RECOMENDACIONES_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l)
                        .containsEntry("compraId", compra.get("compraId").asText())
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
