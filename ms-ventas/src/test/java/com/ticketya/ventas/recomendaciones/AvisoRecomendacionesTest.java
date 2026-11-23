package com.ticketya.ventas.recomendaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.client.RecomendacionesClient;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.service.CompraService;
import com.ticketya.ventas.service.PasarelaPagoService;
import com.ticketya.ventas.service.ReconciliacionPagosJob;
import com.ticketya.ventas.trazabilidad.CapturaLogs;
import com.ticketya.ventas.trazabilidad.RegistroOperaciones;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.ticketya.ventas.recomendaciones.ServiciosSimulados.AVISOS;
import static com.ticketya.ventas.recomendaciones.ServiciosSimulados.cuerpoCompra;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aviso de compra desde ms-ventas (spec preferencias-recomendaciones). PostgreSQL real, clientes HTTP reales y
 * los servicios remotos simulados; el aviso esta activado solo en esta clase (ticketya.recomendaciones.avisar=true).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AvisoRecomendacionesTest {

    @DynamicPropertySource
    static void apuntarAServiciosSimulados(DynamicPropertyRegistry registro) {
        registro.add("ticketya.recomendaciones.avisar", () -> "true");
        registro.add("ticketya.services.eventos-url", () -> ServiciosSimulados.url(ServiciosSimulados.MS_EVENTOS));
        registro.add("ticketya.services.reservas-url", () -> ServiciosSimulados.url(ServiciosSimulados.MS_RESERVAS));
        registro.add("ticketya.services.notificaciones-url",
                () -> ServiciosSimulados.url(ServiciosSimulados.MS_NOTIFICACIONES));
        registro.add("ticketya.services.recomendaciones-url",
                () -> ServiciosSimulados.url(ServiciosSimulados.MS_RECOMENDACIONES));
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
        ServiciosSimulados.reiniciar();
        logs = new CapturaLogs(RecomendacionesClient.class, RegistroOperaciones.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
    }

    @Test
    @DisplayName("Aviso de compra desde ms-ventas: Compra confirmada avisada")
    void compraConfirmadaAvisada() throws Exception {
        JsonNode compra = comprar("k-rec-1", "abc-123", "tok_ok");

        assertThat(AVISOS).singleElement().satisfies(a -> {
            assertThat(a.metodo()).isEqualTo("POST");
            assertThat(a.ruta()).isEqualTo("/internal/compras");
            assertThat(a.correlationId()).isEqualTo("abc-123");
            JsonNode cuerpo = json.readTree(a.cuerpo());
            assertThat(cuerpo.get("compraId").asText()).isEqualTo(compra.get("compraId").asText());
            assertThat(cuerpo.get("clienteId").asText()).isEqualTo("101");
            assertThat(cuerpo.get("eventoIds").toString()).isEqualTo("[42]");
        });
        assertThat(logs.mensajes("RECOMENDACIONES_NO_DISPONIBLE")).isEmpty();
    }

    @Test
    @DisplayName("Aviso de compra desde ms-ventas: ms-recomendaciones lento")
    void msRecomendacionesLento() throws Exception {
        ServiciosSimulados.demoraRecomendacionesMs = 5000;

        long inicio = System.nanoTime();
        JsonNode compra = comprar("k-rec-lento", "corr-lento", "tok_ok");
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(compra.get("estado").asText()).isEqualTo("APROBADA");
        assertThat(duracionMs).isLessThan(2500);
        assertThat(logs.campos("RECOMENDACIONES_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l)
                        .containsEntry("compraId", compra.get("compraId").asText())
                        .containsEntry("correlationId", "corr-lento"));
    }

    @Test
    @DisplayName("Aviso de compra desde ms-ventas: ms-recomendaciones responde con error")
    void msRecomendacionesRespondeConError() throws Exception {
        ServiciosSimulados.statusRecomendaciones = 500;

        JsonNode compra = comprar("k-rec-500", "corr-500", "tok_ok");

        assertThat(compra.get("estado").asText()).isEqualTo("APROBADA");
        assertThat(logs.mensajes("RECOMENDACIONES_NO_DISPONIBLE")).hasSize(1);
    }

    @Test
    @DisplayName("Aviso de compra desde ms-ventas: la reconciliación de un pago incierto también avisa")
    void laReconciliacionTambienAvisa() throws Exception {
        mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-rec-job")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(UUID.randomUUID(), "tok_timeout")))
                .andExpect(status().isGatewayTimeout());
        assertThat(AVISOS).isEmpty();
        String compraId = logs.operaciones("PAGO_INCIERTO").get(0).get("compraId");
        pasarelaPagoService.resolverIncertidumbre(
                CompraService.claveCobroDe(UUID.fromString(compraId)), EstadoPago.APROBADO);

        reconciliacionPagosJob.reconciliarPagosInciertos();

        String estado = jdbc.queryForObject("SELECT estado FROM compra WHERE id = ?", String.class,
                UUID.fromString(compraId));
        assertThat(estado).isEqualTo("APROBADA");
        assertThat(AVISOS).singleElement()
                .satisfies(a -> assertThat(json.readTree(a.cuerpo()).get("compraId").asText()).isEqualTo(compraId));
    }

    private JsonNode comprar(String idempotencyKey, String correlationId, String token) throws Exception {
        MvcResult r = mvc.perform(post("/compras")
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(UUID.randomUUID(), token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }
}
