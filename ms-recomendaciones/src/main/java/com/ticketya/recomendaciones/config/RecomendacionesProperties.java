package com.ticketya.recomendaciones.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Propiedades {@code ticketya.*} de application.yml (design.md D5). */
@ConfigurationProperties(prefix = "ticketya")
@Getter
@Setter
public class RecomendacionesProperties {

    private Services services = new Services();
    private Catalogo catalogo = new Catalogo();

    @Getter
    @Setter
    public static class Services {
        private String eventosUrl = "http://localhost:8081";
        private int eventosConnectTimeoutMs = 1000;
        private int eventosReadTimeoutMs = 2000;
    }

    @Getter
    @Setter
    public static class Catalogo {
        /** Tiempo durante el que la copia del catalogo se usa sin volver a consultar a ms-eventos. */
        private Duration ttl = Duration.ofSeconds(30);
        /** Tope de paginas de 100 eventos que se leen de ms-eventos (2000 eventos). */
        private int maxPaginas = 20;
    }
}
