package com.ticketya.fila.concurrencia;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.fila.IntegracionTest;
import com.ticketya.fila.repository.FilaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia contra el servidor real y Redis real (design.md D3 y D4). Los hilos arrancan juntos con un latch.
 * Varias ejecuciones simultaneas del proceso simulan varias instancias de ms-fila: comparten el mismo Redis, que es
 * donde vive toda la exclusion.
 */
class ConcurrenciaFilaTest extends IntegracionTest {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    @DisplayName("Posiciones únicas con ingresos concurrentes: Muchos ingresos simultáneos")
    void muchosIngresosSimultaneos() throws Exception {
        activarFila(EVENTO, 10, 30);
        int clientes = 200;

        List<Callable<HttpResponse<String>>> tareas = new ArrayList<>();
        for (int i = 1; i <= clientes; i++) {
            String cliente = String.valueOf(1000 + i);
            tareas.add(() -> http.send(pedidoIngreso(cliente), HttpResponse.BodyHandlers.ofString()));
        }
        List<HttpResponse<String>> respuestas = ejecutarJuntos(tareas, 50);

        List<Long> posiciones = new ArrayList<>();
        for (HttpResponse<String> r : respuestas) {
            assertThat(r.statusCode()).isEqualTo(200);
            JsonNode n = json.readTree(r.body());
            assertThat(n.get("estado").asText()).isEqualTo("EN_ESPERA");
            posiciones.add(n.get("posicion").asLong());
        }
        assertThat(posiciones).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, clientes).boxed().toList());
        assertThat(enEspera(EVENTO)).isEqualTo(clientes);
        assertThat(logs.operaciones("INGRESO_FILA")).hasSize(clientes);
        // Los numeros de llegada tambien son 1..200, sin huecos ni repetidos.
        Set<TypedTuple<String>> espera = redis.opsForZSet().rangeWithScores(FilaRepository.espera(EVENTO), 0, -1);
        assertThat(espera).extracting(t -> t.getScore().longValue())
                .containsExactlyElementsOf(LongStream.rangeClosed(1, clientes).boxed().toList());
    }

    @Test
    @DisplayName("Ingreso idempotente: Ingresos simultáneos del mismo cliente")
    void ingresosSimultaneosDelMismoCliente() throws Exception {
        activarFila(EVENTO, 10, 30);

        List<Callable<HttpResponse<String>>> tareas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tareas.add(() -> http.send(pedidoIngreso("101"), HttpResponse.BodyHandlers.ofString()));
        }
        List<HttpResponse<String>> respuestas = ejecutarJuntos(tareas, 20);

        for (HttpResponse<String> r : respuestas) {
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(json.readTree(r.body()).get("posicion").asLong()).isEqualTo(1);
        }
        assertThat(enEspera(EVENTO)).isEqualTo(1);
        assertThat(logs.operaciones("INGRESO_FILA")).hasSize(1);
    }

    @Test
    @DisplayName("Habilitación única con varias instancias: Procesos de habilitación concurrentes")
    void procesosDeHabilitacionConcurrentes() throws Exception {
        activarFila(EVENTO, 10, 30);
        List<String> orden = ingresarEnOrden(100);
        reloj.avanzar(Duration.ofSeconds(30));

        correrProcesoEnParalelo(8);

        assertThat(habilitados(EVENTO)).isEqualTo(10);
        assertThat(enEspera(EVENTO)).isEqualTo(90);
        assertThat(redis.opsForZSet().range(FilaRepository.habilitados(EVENTO), 0, -1))
                .containsExactlyInAnyOrderElementsOf(orden.subList(0, 10));
        List<String> registrados = logs.operaciones("CLIENTE_HABILITADO").stream().map(l -> l.get("clienteId")).toList();
        assertThat(registrados).containsExactlyInAnyOrderElementsOf(orden.subList(0, 10));
    }

    @Test
    @DisplayName("Habilitación única con varias instancias: cinco intervalos con procesos concurrentes")
    void cincoIntervalosConProcesosConcurrentes() throws Exception {
        activarFila(EVENTO, 10, 30);
        List<String> orden = ingresarEnOrden(100);

        for (int intervalo = 0; intervalo < 5; intervalo++) {
            reloj.avanzar(Duration.ofSeconds(30));
            correrProcesoEnParalelo(8);
        }

        List<String> registrados = logs.operaciones("CLIENTE_HABILITADO").stream().map(l -> l.get("clienteId")).toList();
        assertThat(registrados).doesNotHaveDuplicates().hasSize(50);
        assertThat(registrados).containsExactlyInAnyOrderElementsOf(orden.subList(0, 50));
        assertThat(habilitados(EVENTO)).isEqualTo(50);
        assertThat(enEspera(EVENTO)).isEqualTo(50);
        // Cada tanda habilito a los siguientes 10 en orden de llegada: los habilitados de una tanda vencen juntos.
        Map<Double, List<String>> porTanda = redis.opsForZSet()
                .rangeWithScores(FilaRepository.habilitados(EVENTO), 0, -1).stream()
                .collect(Collectors.groupingBy(TypedTuple::getScore,
                        java.util.TreeMap::new, Collectors.mapping(TypedTuple::getValue, Collectors.toList())));
        assertThat(porTanda).hasSize(5);
        int desde = 0;
        for (List<String> tanda : porTanda.values()) {
            assertThat(tanda).containsExactlyInAnyOrderElementsOf(orden.subList(desde, desde + 10));
            desde += 10;
        }
    }

    @Test
    @DisplayName("Registro de operaciones críticas de la fila: Habilitación y vencimiento registrados una vez")
    void habilitacionYVencimientoRegistradosUnaVez() throws Exception {
        activarFila(EVENTO, 10, 30);
        ingresar(EVENTO, "101");
        reloj.avanzar(Duration.ofSeconds(30));
        correrProcesoEnParalelo(8);

        reloj.avanzar(Duration.ofMinutes(10));
        correrProcesoEnParalelo(8);

        assertThat(logs.operaciones("CLIENTE_HABILITADO"))
                .filteredOn(l -> "101".equals(l.get("clienteId")))
                .singleElement()
                .satisfies(l -> assertThat(l).containsEntry("eventoId", "42").containsEntry("usuario", "SISTEMA"));
        assertThat(logs.operaciones("TURNO_VENCIDO"))
                .filteredOn(l -> "101".equals(l.get("clienteId")))
                .singleElement()
                .satisfies(l -> assertThat(l).containsEntry("eventoId", "42").containsEntry("usuario", "SISTEMA"));
    }

    // ------------------------------------------------------------------ utilidades

    private HttpRequest pedidoIngreso(String cliente) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/fila/eventos/" + EVENTO + "/ingresar"))
                .header("X-User-Id", cliente)
                .header("X-User-Rol", "CLIENTE")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /** Ingresa n clientes de a uno y devuelve sus ids en orden de llegada. */
    private List<String> ingresarEnOrden(int n) throws Exception {
        List<String> orden = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String cliente = String.valueOf(2000 + i);
            ingresar(EVENTO, cliente);
            orden.add(cliente);
        }
        return orden;
    }

    private void correrProcesoEnParalelo(int instancias) throws Exception {
        List<Callable<Void>> tareas = new ArrayList<>();
        for (int i = 0; i < instancias; i++) {
            tareas.add(() -> {
                procesador.procesar();
                return null;
            });
        }
        ejecutarJuntos(tareas, instancias);
    }

    /** Ejecuta las tareas con un pool de {@code hilos}; todas esperan el mismo latch para arrancar juntas. */
    private static <T> List<T> ejecutarJuntos(List<Callable<T>> tareas, int hilos) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> f : futuros) {
                resultados.add(f.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
