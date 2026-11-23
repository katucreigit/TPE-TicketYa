package com.ticketya.recomendaciones.controller;

import com.ticketya.recomendaciones.IntegracionTest;
import com.ticketya.recomendaciones.config.IndicesMongo;
import com.ticketya.recomendaciones.model.Compra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.IndexInfo;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro de compras confirmadas (spec "Registro de compras confirmadas"). */
class ComprasInternasTest extends IntegracionTest {

    @Test
    @DisplayName("Registro de compras confirmadas: Compra nueva")
    void compraNueva() throws Exception {
        registrarCompra("c-1", "101", 42L).andExpect(status().isNoContent());

        Compra c = mongo.findById("c-1", Compra.class);
        assertThat(c).isNotNull();
        assertThat(c.getClienteId()).isEqualTo("101");
        assertThat(c.getEventoIds()).containsExactly(42L);
        assertThat(c.getRegistradaEn()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Registro de compras confirmadas: Compra repetida")
    void compraRepetida() throws Exception {
        registrarCompra("c-1", "101", 42L).andExpect(status().isNoContent());
        registrarCompra("c-1", "101", 42L).andExpect(status().isNoContent());

        assertThat(mongo.count(new Query(), Compra.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Registro de compras confirmadas: Compras simultáneas repetidas")
    void comprasSimultaneasRepetidas() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/internal/compras"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"compraId\":\"c-1\",\"clienteId\":\"101\",\"eventoIds\":[42]}"))
                .build();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
            }
            largada.countDown();
            for (Future<Integer> f : futuros) {
                assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(mongo.count(new Query(), Compra.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Registro de compras confirmadas: Compra sin eventos")
    void compraSinEventos() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/compras")
                        .contentType("application/json")
                        .content("{\"compraId\":\"c-2\",\"clienteId\":\"101\",\"eventoIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));

        assertThat(mongo.count(new Query(), Compra.class)).isZero();
    }

    @Test
    @DisplayName("Registro de compras confirmadas: compraId ausente y evento no positivo")
    void otrosCuerposInvalidos() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/compras")
                        .contentType("application/json").content("{\"clienteId\":\"101\",\"eventoIds\":[42]}"))
                .andExpect(status().isBadRequest());
        registrarCompra("c-3", "101", 0L).andExpect(status().isBadRequest());

        assertThat(mongo.count(new Query(), Compra.class)).isZero();
    }

    @Test
    void indicesDeLaColeccionDeCompras() {
        List<String> nombres = mongo.indexOps(Compra.class).getIndexInfo().stream().map(IndexInfo::getName).toList();

        assertThat(nombres).contains(IndicesMongo.IDX_CLIENTE, IndicesMongo.IDX_EVENTOS);
    }
}
