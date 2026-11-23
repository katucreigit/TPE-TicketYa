package com.ticketya.gateway;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rutas /api/preferencias/** y /api/recomendaciones/** del gateway (add-preferencias-recomendaciones, spec
 * "Acceso por el API Gateway"). ms-recomendaciones es un servidor HTTP simulado que guarda lo que recibe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecomendacionesRutaGatewayTest {

    record Recibido(String metodo, String ruta, String userId, String rol) {
    }

    private static final List<Recibido> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer MS_RECOMENDACIONES = simulado();

    @DynamicPropertySource
    static void apuntarAlSimulado(DynamicPropertyRegistry registry) {
        registry.add("MS_RECOMENDACIONES_URL", () -> "http://127.0.0.1:" + MS_RECOMENDACIONES.getAddress().getPort());
    }

    @LocalServerPort
    private int puertoGateway;

    @Value("${jwt.secret}")
    private String secret;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void limpiar() {
        RECIBIDOS.clear();
    }

    @AfterAll
    static void detener() {
        MS_RECOMENDACIONES.stop(0);
    }

    @Test
    @DisplayName("Acceso por el API Gateway: Recomendaciones a través del gateway")
    void recomendacionesATravesDelGateway() throws Exception {
        HttpResponse<String> r = enviar("GET", "/api/recomendaciones?limite=5", token("101", "CLIENTE"));

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(RECIBIDOS).singleElement().satisfies(rec -> {
            assertThat(rec.metodo()).isEqualTo("GET");
            assertThat(rec.ruta()).isEqualTo("/recomendaciones");
            assertThat(rec.userId()).isEqualTo("101");
            assertThat(rec.rol()).isEqualTo("CLIENTE");
        });
    }

    @Test
    @DisplayName("Acceso por el API Gateway: preferencias y también compraron llegan sin /api")
    void otrasRutas() throws Exception {
        String token = token("101", "CLIENTE");

        enviar("PUT", "/api/preferencias", token);
        enviar("GET", "/api/recomendaciones/eventos/42/tambien-compraron", token);

        assertThat(RECIBIDOS).extracting(rec -> rec.metodo() + " " + rec.ruta())
                .containsExactly("PUT /preferencias", "GET /recomendaciones/eventos/42/tambien-compraron");
    }

    @Test
    @DisplayName("Acceso por el API Gateway: Preferencias sin token")
    void preferenciasSinToken() throws Exception {
        assertThat(enviar("GET", "/api/preferencias", null).statusCode()).isEqualTo(401);
        assertThat(enviar("GET", "/api/recomendaciones", null).statusCode()).isEqualTo(401);
        assertThat(RECIBIDOS).isEmpty();
    }

    @Test
    @DisplayName("Acceso por el API Gateway: El registro interno no se expone")
    void elRegistroInternoNoSeExpone() throws Exception {
        String token = token("101", "CLIENTE");

        assertThat(enviar("POST", "/api/internal/compras", token).statusCode()).isEqualTo(404);
        assertThat(enviar("POST", "/internal/compras", token).statusCode()).isEqualTo(404);
        assertThat(RECIBIDOS).isEmpty();
    }

    @Test
    @DisplayName("Acceso por el API Gateway: la ruta tiene su propio circuito")
    void circuitoPropio() {
        assertThat(circuitBreakerRegistry.find("recomendaciones")).isPresent();
    }

    private HttpResponse<String> enviar(String metodo, String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puertoGateway + path))
                .method(metodo, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token(String userId, String rol) {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.builder()
                .subject(userId)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private static HttpServer simulado() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECIBIDOS.add(new Recibido(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-User-Id"),
                        exchange.getRequestHeaders().getFirst("X-User-Rol")));
                byte[] cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, cuerpo.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(cuerpo);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
