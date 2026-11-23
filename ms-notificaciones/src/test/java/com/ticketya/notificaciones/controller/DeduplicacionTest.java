package com.ticketya.notificaciones.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.notificaciones.IntegracionTest;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.query.Query;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Grupo 4: "Deduplicacion por clave". */
@DisplayName("Deduplicacion por clave")
class DeduplicacionTest extends IntegracionTest {

    @LocalServerPort
    private int puerto;

    @Test
    @DisplayName("Pedido repetido con la misma clave")
    void pedidoRepetidoConLaMismaClave() throws Exception {
        String primero = aceptar(pedidoCompra("cliente@example.com", "compra-55"));
        String segundo = aceptar(pedidoCompra("cliente@example.com", "compra-55"));

        assertThat(segundo).isEqualTo(primero);
        assertThat(conClave("compra-55")).isEqualTo(1);
        esperarEstado(primero, EstadoNotificacion.ENVIADA);
        // Despues de enviada, ningun otro ciclo del procesador la vuelve a enviar.
        await().during(Duration.ofMillis(600)).atMost(Duration.ofSeconds(2))
                .until(() -> canal.llamadas(primero) == 1);
    }

    @Test
    @DisplayName("Pedidos sin clave")
    void pedidosSinClave() throws Exception {
        String a = aceptar(pedidoCompra("cliente@example.com", null));
        String b = aceptar(pedidoCompra("cliente@example.com", null));

        assertThat(a).isNotEqualTo(b);
        assertThat(contar()).isEqualTo(2);
    }

    @Test
    @DisplayName("Claves distintas")
    void clavesDistintas() throws Exception {
        String a = aceptar(pedidoCompra("cliente@example.com", "compra-55"));
        String b = aceptar(pedidoCompra("cliente@example.com", "compra-56"));

        assertThat(a).isNotEqualTo(b);
        assertThat(contar()).isEqualTo(2);
    }

    /**
     * 4.3: 20 solicitudes HTTP simultaneas contra el servidor real, cada una en su hilo de Tomcat, liberadas a la
     * vez con un latch. El indice unico decide: una inserta y las demas devuelven la existente.
     */
    @Test
    @DisplayName("Pedidos simultaneos con la misma clave")
    void pedidosSimultaneosConLaMismaClave() throws Exception {
        int hilos = 20;
        String cuerpo = pedidoCompra("cliente@example.com", "compra-77");
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<HttpResponse<String>>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + INTERNO))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                            .build();
                    return http.send(req, HttpResponse.BodyHandlers.ofString());
                }));
            }
            largada.countDown();

            List<String> ids = new ArrayList<>();
            for (Future<HttpResponse<String>> f : futuros) {
                HttpResponse<String> r = f.get(30, TimeUnit.SECONDS);
                assertThat(r.statusCode()).as(r.body()).isEqualTo(202);
                JsonNode nodo = json.readTree(r.body());
                ids.add(nodo.get("id").asText());
            }
            assertThat(ids).hasSize(hilos).containsOnly(ids.get(0));
            assertThat(conClave("compra-77")).isEqualTo(1);
            assertThat(contar()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private long conClave(String clave) {
        return mongo.count(new Query(where("claveDeduplicacion").is(clave)), Notificacion.class);
    }
}
