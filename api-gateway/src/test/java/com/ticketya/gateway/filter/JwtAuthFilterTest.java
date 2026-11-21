package com.ticketya.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JwtAuthFilterTest {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Value("${jwt.secret}")
    private String secret;

    @BeforeEach
    void configurarFiltro() {
        ReflectionTestUtils.setField(jwtAuthFilter, "secret", secret);
    }

    @Test
    void solicitudSinTokenDevuelve401() {
        MockServerWebExchange exchange = crearSolicitud();

        ejecutar(exchange);

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenInvalidoDevuelve401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/reservas")
                        .header("Authorization", "Bearer token-invalido")
                        .build()
        );

        ejecutar(exchange);

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenVencidoDevuelve401() {
        String token = generarToken("123", "CLIENTE", -1000);

        MockServerWebExchange exchange = crearSolicitudConToken(token);

        ejecutar(exchange);

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenConRolDesconocidoDevuelve401() {
        String token = generarToken("123", "ROL_INEXISTENTE", 60000);

        MockServerWebExchange exchange = crearSolicitudConToken(token);

        ejecutar(exchange);

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenValidoPermiteContinuarLaSolicitud() {
        String token = generarToken("123", "CLIENTE", 60000);

        MockServerWebExchange exchange = crearSolicitudConToken(token);
        AtomicReference<ServerWebExchange> exchangeRecibido = new AtomicReference<>();

        jwtAuthFilter.filter(exchange, exchangeMutado -> {
            exchangeRecibido.set(exchangeMutado);
            return Mono.empty();
        }).block();

        assertThat(exchangeRecibido.get()).isNotNull();
        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst("X-User-Id")).isEqualTo("123");
        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst("X-User-Rol")).isEqualTo("CLIENTE");
    }

    @Test
    void tokenValidoReemplazaLosHeadersFalsificados() {
        String token = generarToken("123", "ADMIN", 60000);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "999")
                        .header("X-User-Rol", "CLIENTE")
                        .build()
        );

        AtomicReference<ServerWebExchange> exchangeRecibido = new AtomicReference<>();

        jwtAuthFilter.filter(exchange, exchangeMutado -> {
            exchangeRecibido.set(exchangeMutado);
            return Mono.empty();
        }).block();

        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst("X-User-Id")).isEqualTo("123");
        assertThat(exchangeRecibido.get().getRequest().getHeaders()
                .getFirst("X-User-Rol")).isEqualTo("ADMIN");
    }

    private MockServerWebExchange crearSolicitud() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/reservas").build()
        );
    }

    private MockServerWebExchange crearSolicitudConToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );
    }

    private void ejecutar(MockServerWebExchange exchange) {
        jwtAuthFilter.filter(exchange, exchangeMutado -> Mono.empty()).block();
    }

    private String generarToken(String userId, String rol, long duracionMs) {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        return Jwts.builder()
                .subject(userId)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + duracionMs))
                .signWith(key)
                .compact();
    }
}
