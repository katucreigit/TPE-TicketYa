
package com.ticketya.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Set;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Set<String> ROLES_VALIDOS = Set.of(
            "CLIENTE",
            "ORGANIZADOR",
            "ADMIN",
            "PERSONAL_ACCESO"
    );

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        // Rutas publicas: auth + lectura de eventos y lugares sin login
        if (esPublica(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return responderNoAutorizado(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parsearToken(token);

            String userId = claims.getSubject();
            String rol = claims.get("rol", String.class);

            // Verifica que el token contenga una identidad y un rol validos.
            if (userId == null || userId.isBlank()
                    || rol == null || !ROLES_VALIDOS.contains(rol)) {
                return responderNoAutorizado(exchange);
            }

            // Reemplaza los headers del cliente por los datos confiables del token.
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Rol");
                        headers.add("X-User-Id", userId);
                        headers.add("X-User-Rol", rol);
                    }))
                    .build();

            return chain.filter(mutated);

        } catch (JwtException | IllegalArgumentException e) {
            return responderNoAutorizado(exchange);
        }
    }

    private Mono<Void> responderNoAutorizado(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean esPublica(String path, HttpMethod method) {
        // Login y register siempre publicos
        if (path.startsWith("/api/auth/")) return true;

        // Buscar y ver eventos/lugares no requiere login
        if (method == HttpMethod.GET &&
                (path.startsWith("/api/eventos") || path.startsWith("/api/lugares"))) return true;

        return false;
    }

    private Claims parsearToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return 0;
}
}
