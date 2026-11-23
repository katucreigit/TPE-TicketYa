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

        // Las solicitudes preflight de CORS no requieren autenticacion.
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(eliminarHeadersIdentidad(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        // Las rutas de autenticacion son publicas y no necesitan identidad previa.
        if (esRutaAuth(path)) {
            return chain.filter(exchange);
        }

        boolean rutaPublica = esPublica(path, method);

        // En rutas publicas se permite continuar sin token, pero se eliminan
        // los headers de identidad que pudiera haber enviado el cliente.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (rutaPublica && authHeader == null) {
                ServerWebExchange mutated = eliminarHeadersIdentidad(exchange);
                return chain.filter(mutated);
            }

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

    private ServerWebExchange eliminarHeadersIdentidad(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(r -> r.headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Rol");
                }))
                .build();
    }

    private Mono<Void> responderNoAutorizado(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean esRutaAuth(String path) {
        return path.equals("/api/auth") || path.startsWith("/api/auth/");
    }

    private boolean esPublica(String path, HttpMethod method) {
        if (method != HttpMethod.GET) {
            return false;
        }

        return path.equals("/api/eventos")
                || path.startsWith("/api/eventos/")
                || path.equals("/api/lugares")
                || path.startsWith("/api/lugares/");
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
