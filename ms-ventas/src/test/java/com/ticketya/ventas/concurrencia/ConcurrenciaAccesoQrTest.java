package com.ticketya.ventas.concurrencia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.NotificacionesClient;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.model.enums.TipoMedioPago;
import com.ticketya.ventas.repository.CompraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia real contra servidor Tomcat (RANDOM_PORT) y PostgreSQL 16 (Testcontainers):
 * N escaneos simultaneos del mismo QR, e ingreso simultaneo con devolucion o cancelacion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConcurrenciaAccesoQrTest {

    private static final long EVENTO = 42L;
    private static final BigDecimal PRECIO = new BigDecimal("45000.00");

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private EventosClient eventosClient;

    @MockitoBean
    private ReservasClient reservasClient;

    @MockitoBean
    private NotificacionesClient notificacionesClient;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM reintegro");
        jdbc.update("DELETE FROM devolucion");
        jdbc.update("DELETE FROM entrada");
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM compra");
    }

    @Test
    void nEscaneosSimultaneosDelMismoQrPermitenExactamenteUnoYRechazanLosDemas() throws Exception {
        Entrada entrada = crearEntradaEmitida();
        int hilos = 20;

        List<Resultado> resultados = enParalelo(hilos, i ->
                validarQr(entrada.getCodigoQr(), EVENTO, "acc-" + i));

        assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(
                Map.of(200, 1L, 409, (long) (hilos - 1)));

        Resultado ganadora = resultados.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        String operadorGanador = ganadora.cuerpo().get("validadaPor").asText();
        String usadaEnGanadora = ganadora.cuerpo().get("usadaEn").asText();
        assertThat(ganadora.cuerpo().get("estado").asText()).isEqualTo("USADA");
        assertThat(operadorGanador).startsWith("acc-");
        assertThat(usadaEnGanadora).isNotBlank();

        assertThat(resultados).filteredOn(r -> r.status() == 409)
                .allSatisfy(r -> {
                    assertThat(r.codigo()).isEqualTo("ENTRADA_USADA");
                    assertThat(r.cuerpo().get("message").asText()).isNotBlank();
                });

        var fila = jdbc.queryForMap(
                "SELECT estado, usada_en, validada_por FROM entrada WHERE id = ?", entrada.getId());
        assertThat(fila.get("estado")).isEqualTo("USADA");
        assertThat(fila.get("usada_en")).isNotNull();
        assertThat(fila.get("validada_por")).isEqualTo(operadorGanador);
    }

    @RepeatedTest(5)
    void ingresoYDevolucionSimultaneosSobreLaMismaEntradaSoloUnoGana() throws Exception {
        Entrada entrada = crearEntradaEmitida();

        List<Resultado> resultados = enParalelo(2, i -> i == 0
                ? validarQr(entrada.getCodigoQr(), EVENTO, "acc-7")
                : solicitarDevolucion(entrada.getId(), "cli-100"));

        Resultado ingreso = resultados.get(0);
        Resultado devolucion = resultados.get(1);
        var fila = jdbc.queryForMap(
                "SELECT estado, usada_en, validada_por FROM entrada WHERE id = ?", entrada.getId());
        Integer devoluciones = jdbc.queryForObject(
                "SELECT count(*) FROM devolucion WHERE entrada_id = ?", Integer.class, entrada.getId());

        if (ingreso.status() == 200) {
            assertThat(devolucion.status()).isEqualTo(409);
            assertThat(devolucion.codigo()).isEqualTo("ENTRADA_NO_DEVOLVIBLE");
            assertThat(fila.get("estado")).isEqualTo("USADA");
            assertThat(fila.get("usada_en")).isNotNull();
            assertThat(fila.get("validada_por")).isEqualTo("acc-7");
            assertThat(devoluciones).isZero();
        } else {
            assertThat(ingreso.status()).isEqualTo(409);
            assertThat(ingreso.codigo()).isEqualTo("ENTRADA_NO_VALIDA");
            assertThat(devolucion.status()).isEqualTo(201);
            assertThat(fila.get("estado")).isEqualTo("DEVOLUCION_SOLICITADA");
            assertThat(fila.get("usada_en")).isNull();
            assertThat(fila.get("validada_por")).isNull();
            assertThat(devoluciones).isEqualTo(1);
        }
    }

    @RepeatedTest(5)
    void ingresoYCancelacionEventoSimultaneosSobreLaMismaEntradaSoloUnoGana() throws Exception {
        Entrada entrada = crearEntradaEmitida();

        List<Resultado> resultados = enParalelo(2, i -> i == 0
                ? validarQr(entrada.getCodigoQr(), EVENTO, "acc-7")
                : cancelarEvento(EVENTO));

        Resultado ingreso = resultados.get(0);
        Resultado cancelacion = resultados.get(1);
        assertThat(cancelacion.status()).isEqualTo(200);

        var fila = jdbc.queryForMap(
                "SELECT estado, usada_en, validada_por FROM entrada WHERE id = ?", entrada.getId());
        Integer reintegros = jdbc.queryForObject(
                "SELECT count(*) FROM reintegro WHERE entrada_id = ?", Integer.class, entrada.getId());

        if (ingreso.status() == 200) {
            assertThat(fila.get("estado")).isEqualTo("USADA");
            assertThat(fila.get("usada_en")).isNotNull();
            assertThat(fila.get("validada_por")).isEqualTo("acc-7");
            assertThat(reintegros).isZero();
        } else {
            assertThat(ingreso.status()).isEqualTo(409);
            assertThat(ingreso.codigo()).isEqualTo("ENTRADA_NO_VALIDA");
            assertThat(fila.get("estado")).isEqualTo("REINTEGRADA");
            assertThat(fila.get("usada_en")).isNull();
            assertThat(fila.get("validada_por")).isNull();
            assertThat(reintegros).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Entrada crearEntradaEmitida() {
        Compra compra = new Compra();
        compra.setReservaId(UUID.randomUUID());
        compra.setClienteId("cli-100");
        compra.setEventoId(EVENTO);
        compra.setIdempotencyKey("k-" + UUID.randomUUID());
        compra.setRequestHash("a".repeat(64));
        compra.setTotal(PRECIO);
        compra.setEstado(EstadoCompra.APROBADA);

        Pago pago = new Pago();
        pago.setMedioPagoTipo(TipoMedioPago.TARJETA_CREDITO);
        pago.setUltimos4("4242");
        pago.setMonto(PRECIO);
        pago.setEstado(EstadoPago.APROBADO);
        pago.setTransaccionPasarela("tx-" + UUID.randomUUID());
        compra.setPagoBidireccional(pago);

        Entrada entrada = new Entrada();
        entrada.setEventoId(EVENTO);
        entrada.setSectorId(11L);
        entrada.setFila(3);
        entrada.setNumero(10);
        entrada.setPrecioUnitario(PRECIO);
        entrada.setEventoFechaHora(LocalDateTime.now().plusDays(10));
        entrada.setCodigoQr(UUID.randomUUID().toString());
        entrada.setEstado(EstadoEntrada.EMITIDA);
        compra.agregarEntrada(entrada);

        return compraRepository.saveAndFlush(compra).getEntradas().get(0);
    }

    record Resultado(int status, JsonNode cuerpo) {
        String codigo() {
            return cuerpo.path("code").asText();
        }
    }

    private HttpRequest validarQr(String codigoQr, long eventoId, String operadorId) {
        String cuerpo = """
                {"codigoQr": "%s", "eventoId": %d}""".formatted(codigoQr, eventoId);
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/entradas/validar"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-User-Id", operadorId)
                .header("X-User-Rol", "PERSONAL_ACCESO")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
    }

    private HttpRequest solicitarDevolucion(UUID entradaId, String clienteId) {
        String cuerpo = """
                {"entradaId": "%s", "motivo": "No puedo asistir"}""".formatted(entradaId);
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/devoluciones"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-User-Id", clienteId)
                .header("X-User-Rol", "CLIENTE")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
    }

    private HttpRequest cancelarEvento(long eventoId) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/internal/eventos/" + eventoId + "/cancelacion"))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private List<Resultado> enParalelo(int n, IntFunction<HttpRequest> solicitud) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch listos = new CountDownLatch(n);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                HttpRequest request = solicitud.apply(i);
                futuros.add(hilos.submit(() -> {
                    listos.countDown();
                    largada.await();
                    HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
                    return new Resultado(r.statusCode(), r.body().isBlank() ? json.nullNode() : json.readTree(r.body()));
                }));
            }
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> f : futuros) {
                resultados.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(resultados).as("ninguna solicitud termina en error interno")
                    .noneMatch(r -> r.status() >= 500);
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private static Map<Integer, Long> contarPorStatus(List<Resultado> resultados) {
        return resultados.stream().collect(Collectors.groupingBy(Resultado::status, Collectors.counting()));
    }
}
