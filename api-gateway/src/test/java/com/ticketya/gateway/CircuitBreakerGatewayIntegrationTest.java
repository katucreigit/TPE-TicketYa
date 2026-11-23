package com.ticketya.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "MS_AUTH_URL=http://127.0.0.1:1",
                "MS_RESERVAS_URL=http://127.0.0.1:1",
                "MS_EVENTOS_URL=http://127.0.0.1:1",
                "MS_VENTAS_URL=http://127.0.0.1:1"
        }
)
class CircuitBreakerGatewayIntegrationTest {

    @LocalServerPort
    private int puertoGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${jwt.secret}")
    private String secret;

    @Test
    void debeRegistrarUnCircuitoIndependienteParaCadaMicroservicio() {
        assertThat(circuitBreakerRegistry.find("auth")).isPresent();
        assertThat(circuitBreakerRegistry.find("reservas")).isPresent();
        assertThat(circuitBreakerRegistry.find("eventos")).isPresent();
        assertThat(circuitBreakerRegistry.find("ventas")).isPresent();
    }

    @Test
    void debeRecuperarseAutomaticamenteDespuesDelPeriodoAbierto() throws Exception {
        CircuitBreaker circuito = circuitBreakerRegistry.circuitBreaker("eventos");

        circuito.reset();
        circuito.transitionToOpenState();

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(11_000);

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        for (int i = 0; i < 3; i++) {
            circuito.executeSupplier(() -> "OK");
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void debeAbrirCircuitoEventosCuandoMicroservicioAcumulaFallos() throws Exception {
        verificarCircuitoAbierto("eventos", "/api/eventos", "GET", false);
    }

    @Test
    void debeAbrirCircuitoAuthCuandoMicroservicioAcumulaFallos() throws Exception {
        verificarCircuitoAbierto("auth", "/api/auth/login", "POST", false);
    }

    @Test
    void debeAbrirCircuitoReservasCuandoMicroservicioAcumulaFallos() throws Exception {
        verificarCircuitoAbierto("reservas", "/api/reservas", "POST", true);
    }

    @Test
    void debeAbrirCircuitoVentasCuandoMicroservicioAcumulaFallos() throws Exception {
        verificarCircuitoAbierto("ventas", "/api/compras", "GET", true);
    }

    private void verificarCircuitoAbierto(
            String nombreCircuito,
            String path,
            String metodo,
            boolean requiereToken
    ) throws Exception {
        CircuitBreaker circuito = circuitBreakerRegistry.circuitBreaker(nombreCircuito);
        circuito.reset();

        HttpClient cliente = HttpClient.newHttpClient();
        HttpRequest.Builder constructor = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puertoGateway + path));

        if (requiereToken) {
            constructor.header("Authorization", "Bearer " + generarToken());
        }

        HttpRequest solicitud = constructor
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();

        for (int i = 0; i < 5; i++) {
            HttpResponse<String> respuesta = cliente.send(
                    solicitud,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(respuesta.statusCode()).isEqualTo(503);
            assertThat(respuesta.body()).contains("SERVICIO_NO_DISPONIBLE");
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private String generarToken() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        return Jwts.builder()
                .subject("123")
                .claim("rol", "CLIENTE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
