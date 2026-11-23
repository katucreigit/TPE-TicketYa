package com.ticketya.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenario "ms-recomendaciones no disponible": con la URL apuntando a un puerto sin servicio, el gateway responde
 * con el fallback comun (503 SERVICIO_NO_DISPONIBLE). Contexto propio por la URL distinta.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MS_RECOMENDACIONES_URL=http://127.0.0.1:1"
)
class RecomendacionesNoDisponibleGatewayTest {

    @LocalServerPort
    private int puertoGateway;

    @Value("${jwt.secret}")
    private String secret;

    @Test
    @DisplayName("Acceso por el API Gateway: ms-recomendaciones no disponible")
    void msRecomendacionesNoDisponible() throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puertoGateway + "/api/recomendaciones"))
                .header("Authorization", "Bearer " + token())
                .GET()
                .build();

        HttpResponse<String> respuesta = HttpClient.newHttpClient()
                .send(solicitud, HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(respuesta.body()).contains("SERVICIO_NO_DISPONIBLE");
    }

    private String token() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.builder()
                .subject("101")
                .claim("rol", "CLIENTE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
