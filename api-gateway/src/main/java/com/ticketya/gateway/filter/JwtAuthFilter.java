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

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

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
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parsearToken(token);
            String userId = claims.getSubject();
            String rol = claims.get("rol", String.class);

            // Reemplaza (no suma) los headers X-User-Id y X-User-Rol para los microservicios downstream
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Rol");
                        headers.add("X-User-Id", userId);
                        headers.add("X-User-Rol", rol);
                    }))
                    .build();

            return chain.filter(mutated);

        } catch (JwtException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
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
        return -1; // corre antes que los filtros de ruteo
    }
}
