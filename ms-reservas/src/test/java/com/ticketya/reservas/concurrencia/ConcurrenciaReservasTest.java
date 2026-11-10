package com.ticketya.reservas.concurrencia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static com.ticketya.reservas.Escenario.noNumerado;
import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.2, 3.3 y 3.4: solicitudes HTTP simultaneas contra el servidor real (puerto aleatorio) y PostgreSQL real.
 * Cada solicitud corre en su propio hilo de Tomcat con su conexion del pool, como en produccion; todas arrancan
 * a la vez con un latch. Ningun resultado puede ser 500 (un deadlock o un error de integridad lo producirian).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConcurrenciaReservasTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;
    private static final long CAMPO = 12L;

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private EventosClient eventosClient;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
    }

    /** 3.2 Un solo ganador por asiento. */
    @Nested
    class MismoAsiento {

        @Test
        void cincuentaClientesReservanElMismoAsientoYExactamenteUnoRecibe201() throws Exception {
            e.inventario(EVENTO, 4, numerado(PLATEA, 5, 10));

            List<Resultado> resultados = enParalelo(50, i ->
                    reserva("cliente-" + i, "{\"eventoId\": 42, \"sectorId\": 11, \"asientos\": [{\"fila\": 3, \"numero\": 10}]}"));

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, 49L));
            assertThat(resultados).filteredOn(r -> r.status() == 409)
                    .allSatisfy(r -> {
                        assertThat(r.codigo()).isEqualTo("ASIENTO_NO_DISPONIBLE");
                        assertThat(r.cuerpo().get("details").get(0).asText()).isEqualTo("F3-N10");
                    });

            String ganadora = resultados.stream().filter(r -> r.status() == 201).findFirst().orElseThrow()
                    .cuerpo().get("reservaId").asText();
            var asiento = jdbc.queryForMap("SELECT estado, CAST(reserva_id AS text) AS reserva_id, version "
                    + "FROM asiento WHERE evento_id = 42 AND sector_id = 11 AND fila = 3 AND numero = 10");
            assertThat(asiento.get("estado")).isEqualTo("RESERVADO");
            assertThat(asiento.get("reserva_id")).isEqualTo(ganadora);
            assertThat(((Number) asiento.get("version")).longValue()).isEqualTo(1L);
            assertThat(e.entero("SELECT count(*) FROM reserva")).isEqualTo(1);
            assertThat(e.entero("SELECT count(*) FROM reserva_item")).isEqualTo(1);
        }

        /**
         * Pedidos de 3 asientos sobre 4, en distinto orden: cualquier par se superpone, asi que hay un unico
         * ganador. Como los UPDATE se ejecutan ordenados por fila y numero, no hay deadlocks (serian 500).
         */
        @RepeatedTest(3)
        void pedidosConAsientosEnComunYEnDistintoOrdenNoProducenDeadlocks() throws Exception {
            e.inventario(EVENTO, 4, numerado(PLATEA, 1, 4));
            String[] pedidos = {
                    "[{\"fila\":1,\"numero\":3},{\"fila\":1,\"numero\":2},{\"fila\":1,\"numero\":1}]",
                    "[{\"fila\":1,\"numero\":2},{\"fila\":1,\"numero\":4},{\"fila\":1,\"numero\":3}]",
                    "[{\"fila\":1,\"numero\":4},{\"fila\":1,\"numero\":1},{\"fila\":1,\"numero\":3}]",
                    "[{\"fila\":1,\"numero\":1},{\"fila\":1,\"numero\":4},{\"fila\":1,\"numero\":2}]"};

            List<Resultado> resultados = enParalelo(40, i ->
                    reserva("cliente-" + i, "{\"eventoId\": 42, \"sectorId\": 11, \"asientos\": " + pedidos[i % 4] + "}"));

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, 39L));
            assertThat(e.entero("SELECT count(*) FROM asiento WHERE estado = 'RESERVADO'")).isEqualTo(3);
            assertThat(e.entero("SELECT count(DISTINCT reserva_id) FROM asiento WHERE estado = 'RESERVADO'"))
                    .isEqualTo(1);
            assertThat(e.entero("SELECT count(*) FROM asiento WHERE estado = 'LIBRE' AND reserva_id IS NULL"))
                    .isEqualTo(1);
        }
    }

    /** 3.3 El ultimo cupo de un sector no numerado. */
    @Nested
    class UltimoCupo {

        @Test
        void veinteSolicitudesPorElUltimoLugarYSoloUnaLoObtiene() throws Exception {
            e.inventario(EVENTO, 4, noNumerado(CAMPO, 1));

            List<Resultado> resultados = conMonitorDeCupo(() -> enParalelo(20, i ->
                    reserva("cliente-" + i, "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 1}")));

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, 19L));
            assertThat(resultados).filteredOn(r -> r.status() == 409)
                    .allSatisfy(r -> assertThat(r.codigo()).isEqualTo("CUPO_INSUFICIENTE"));
            assertThat(e.disponibles(EVENTO, CAMPO)).isZero();
            assertThat(e.entero("SELECT COALESCE(SUM(cantidad), 0) FROM reserva")).isEqualTo(1);
        }

        @Test
        void elCupoSeRepartePorCompletoSinSobreventaYNuncaQuedaNegativo() throws Exception {
            e.inventario(EVENTO, 4, noNumerado(CAMPO, 10));

            // 30 solicitudes de 1 o 2 lugares compiten por 10 lugares.
            List<Resultado> resultados = conMonitorDeCupo(() -> enParalelo(30, i ->
                    reserva("cliente-" + i, "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": " + (1 + i % 2) + "}")));

            assertThat(resultados).allSatisfy(r -> assertThat(r.status()).isIn(201, 409));
            int vendidos = resultados.stream().filter(r -> r.status() == 201)
                    .mapToInt(r -> r.cuerpo().get("cantidad").asInt()).sum();
            int disponibles = e.disponibles(EVENTO, CAMPO);
            assertThat(disponibles).isBetween(0, 1);
            assertThat(vendidos + disponibles).as("lo reservado mas lo disponible es la capacidad").isEqualTo(10);
            assertThat(e.entero("SELECT SUM(cantidad) FROM reserva")).isEqualTo(vendidos);
        }
    }

    /** 3.4 El limite por cliente con solicitudes simultaneas del mismo cliente. */
    @Nested
    class LimiteSimultaneo {

        @RepeatedTest(5)
        void dosReservasDeTresALaVezConLimiteCuatroYALoSumoUnaTieneExito() throws Exception {
            e.inventario(EVENTO, 4, noNumerado(CAMPO, 100));

            List<Resultado> resultados = enParalelo(2, i ->
                    reserva("101", "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 3}"));

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 422, 1L));
            assertThat(resultados).filteredOn(r -> r.status() == 422)
                    .allSatisfy(r -> assertThat(r.codigo()).isEqualTo("LIMITE_POR_CLIENTE"));
            assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(97);
        }

        @Test
        void diezReservasDeUnLugarDelMismoClienteYSoloEntranCuatro() throws Exception {
            e.inventario(EVENTO, 4, noNumerado(CAMPO, 100));

            List<Resultado> resultados = enParalelo(10, i ->
                    reserva("101", "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 1}"));

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 4L, 422, 6L));
            assertThat(e.entero("SELECT SUM(cantidad) FROM reserva WHERE cliente_id = '101'")).isEqualTo(4);
            assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(96);
        }

        @Test
        void elMismoClienteEnUnSectorNumeradoYEnUnoNoNumeradoALaVez() throws Exception {
            e.inventario(EVENTO, 4, numerado(PLATEA, 5, 10), noNumerado(CAMPO, 100));

            List<Resultado> resultados = enParalelo(2, i -> i == 0
                    ? reserva("101", "{\"eventoId\": 42, \"sectorId\": 11, \"asientos\": "
                    + "[{\"fila\":1,\"numero\":1},{\"fila\":1,\"numero\":2},{\"fila\":1,\"numero\":3}]}")
                    : reserva("101", "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 3}"));

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 422, 1L));
            int asientos = e.entero("SELECT count(*) FROM asiento WHERE estado = 'RESERVADO'");
            int cupos = 100 - e.disponibles(EVENTO, CAMPO);
            assertThat(asientos + cupos).isEqualTo(3);
        }

        /**
         * Clientes distintos no se serializan entre si. Son el doble del pool de conexiones (10): si el aviso de
         * agotado se evaluara reteniendo la conexion de la reserva, el pool se agotaria y las solicitudes
         * quedarian esperando hasta el timeout.
         */
        @Test
        void clientesDistintosNoSeBloqueanPorElLimiteNiAgotanElPool() throws Exception {
            e.inventario(EVENTO, 4, noNumerado(CAMPO, 100));

            long inicio = System.nanoTime();
            List<Resultado> resultados = enParalelo(20, i ->
                    reserva("cliente-" + i, "{\"eventoId\": 42, \"sectorId\": 12, \"cantidad\": 4}"));
            Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(contarPorStatus(resultados)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 20L));
            assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(20);
            assertThat(duracion).as("sin esperas por conexiones del pool").isLessThan(Duration.ofSeconds(10));
        }
    }

    // ------------------------------------------------------------------ infraestructura

    record Resultado(int status, JsonNode cuerpo) {
        String codigo() {
            return cuerpo.path("code").asText();
        }
    }

    private HttpRequest reserva(String clienteId, String cuerpo) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/reservas"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-User-Id", clienteId)
                .header("X-User-Rol", "CLIENTE")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
    }

    /** Envia n solicitudes a la vez: cada hilo espera en el latch de largada y todos salen juntos. */
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
            assertThat(resultados).as("ninguna solicitud termina en error interno (deadlock o integridad)")
                    .noneMatch(r -> r.status() >= 500);
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private interface Carrera {
        List<Resultado> correr() throws Exception;
    }

    /** Mientras corre la carrera, lee el cupo del sector una y otra vez y verifica que nunca sea negativo. */
    private List<Resultado> conMonitorDeCupo(Carrera carrera) throws Exception {
        AtomicBoolean corriendo = new AtomicBoolean(true);
        AtomicInteger minimo = new AtomicInteger(Integer.MAX_VALUE);
        Thread monitor = new Thread(() -> {
            while (corriendo.get()) {
                minimo.accumulateAndGet(e.disponibles(EVENTO, CAMPO), Math::min);
            }
        });
        monitor.start();
        try {
            return carrera.correr();
        } finally {
            corriendo.set(false);
            monitor.join(5000);
            assertThat(minimo.get()).as("disponibles observado durante la carrera").isGreaterThanOrEqualTo(0);
        }
    }

    private static Map<Integer, Long> contarPorStatus(List<Resultado> resultados) {
        return resultados.stream().collect(Collectors.groupingBy(Resultado::status, Collectors.counting()));
    }
}
