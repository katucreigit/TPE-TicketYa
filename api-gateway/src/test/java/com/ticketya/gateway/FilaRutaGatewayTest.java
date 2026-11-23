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
 * Ruta /api/fila/** del gateway (add-fila-virtual, spec fila-virtual "Acceso a la fila por el API Gateway").
 * ms-fila y ms-eventos son servidores HTTP simulados que guardan lo que reciben.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilaRutaGatewayTest {

    record Recibido(String servicio, String metodo, String ruta, String userId, String rol) {
    }

    private static final List<Recibido> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer MS_FILA = simulado("ms-fila");
    private static final HttpServer MS_EVENTOS = simulado("ms-eventos");

    @DynamicPropertySource
    static void apuntarALosSimulados(DynamicPropertyRegistry registry) {
        registry.add("MS_FILA_URL", () -> "http://127.0.0.1:" + MS_FILA.getAddress().getPort());
        registry.add("MS_EVENTOS_URL", () -> "http://127.0.0.1:" + MS_EVENTOS.getAddress().getPort());
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
        MS_FILA.stop(0);
        MS_EVENTOS.stop(0);
    }

    @Test
    @DisplayName("Acceso a la fila por el API Gateway: Ingreso a través del gateway")
    void ingresoATravesDelGateway() throws Exception {
        HttpResponse<String> r = enviar("POST", "/api/fila/eventos/42/ingresar", token("101", "CLIENTE"));

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(RECIBIDOS).singleElement().satisfies(rec -> {
            assertThat(rec.servicio()).isEqualTo("ms-fila");
            assertThat(rec.metodo()).isEqualTo("POST");
            assertThat(rec.ruta()).isEqualTo("/fila/eventos/42/ingresar");
            assertThat(rec.userId()).isEqualTo("101");
            assertThat(rec.rol()).isEqualTo("CLIENTE");
        });
    }

    @Test
    @DisplayName("Acceso a la fila por el API Gateway: la identidad enviada por el cliente se reemplaza por la del token")
    void identidadDelToken() throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puertoGateway + "/api/fila/eventos/42/activar"))
                .header("Authorization", "Bearer " + token("300", "ORGANIZADOR"))
                .header("X-User-Id", "1")
                .header("X-User-Rol", "ADMIN")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        http.send(solicitud, HttpResponse.BodyHandlers.ofString());

        assertThat(RECIBIDOS).singleElement().satisfies(rec -> {
            assertThat(rec.ruta()).isEqualTo("/fila/eventos/42/activar");
            assertThat(rec.userId()).isEqualTo("300");
            assertThat(rec.rol()).isEqualTo("ORGANIZADOR");
        });
    }

    @Test
    @DisplayName("Acceso a la fila por el API Gateway: Rutas de eventos sin cambios")
    void rutasDeEventosSinCambios() throws Exception {
        HttpResponse<String> r = enviar("GET", "/api/eventos/42", null);

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(RECIBIDOS).singleElement().satisfies(rec -> {
            assertThat(rec.servicio()).isEqualTo("ms-eventos");
            assertThat(rec.ruta()).isEqualTo("/eventos/42");
        });
    }

    @Test
    @DisplayName("Acceso a la fila por el API Gateway: Fila sin token")
    void filaSinToken() throws Exception {
        HttpResponse<String> r = enviar("POST", "/api/fila/eventos/42/ingresar", null);

        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(RECIBIDOS).isEmpty();
    }

    @Test
    @DisplayName("Acceso a la fila por el API Gateway: La consulta interna no se expone")
    void laConsultaInternaNoSeExpone() throws Exception {
        String token = token("101", "CLIENTE");

        HttpResponse<String> conApi = enviar("GET", "/api/internal/fila/eventos/42/clientes/101/estado", token);
        HttpResponse<String> sinApi = enviar("GET", "/internal/fila/eventos/42/clientes/101/estado", token);

        assertThat(conApi.statusCode()).isEqualTo(404);
        assertThat(sinApi.statusCode()).isEqualTo(404);
        assertThat(RECIBIDOS).isEmpty();
    }

    @Test
    @DisplayName("Acceso a la fila por el API Gateway: la ruta tiene su propio circuito")
    void circuitoPropio() {
        assertThat(circuitBreakerRegistry.find("fila")).isPresent();
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

    private static HttpServer simulado(String servicio) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECIBIDOS.add(new Recibido(servicio, exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
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
