package com.ticketya.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdGatewayIntegrationTest {

    private static HttpServer servidor;
    private static final AtomicReference<String> correlationIdRecibido =
            new AtomicReference<>();

    @Value("${local.server.port}")
    private int puertoGateway;

    @DynamicPropertySource
    static void configurarServidorSimulado(DynamicPropertyRegistry registry) {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

            servidor.createContext("/", exchange -> {
                correlationIdRecibido.set(
                        exchange.getRequestHeaders().getFirst("X-Correlation-Id")
                );

                byte[] respuesta = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, respuesta.length);

                try (var output = exchange.getResponseBody()) {
                    output.write(respuesta);
                }
            });

            servidor.start();

            registry.add("MS_EVENTOS_URL", () ->
                    "http://127.0.0.1:" + servidor.getAddress().getPort()
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo iniciar el servidor HTTP simulado", e
            );
        }
    }

    @AfterAll
    static void detenerServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void debePropagarElMismoCorrelationIdAlMicroservicio() throws Exception {
        String correlationId = "compra-integracion-123";

        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puertoGateway + "/api/eventos"))
                .header("X-Correlation-Id", correlationId)
                .GET()
                .build();

        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                solicitud,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(respuesta.statusCode()).isEqualTo(200);

        assertThat(correlationIdRecibido.get())
                .isEqualTo(correlationId);

        assertThat(respuesta.headers().firstValue("X-Correlation-Id"))
                .contains(correlationId);
    }
}
