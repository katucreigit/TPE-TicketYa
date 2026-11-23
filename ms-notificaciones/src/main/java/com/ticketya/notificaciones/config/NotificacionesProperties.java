package com.ticketya.notificaciones.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Propiedades {@code ticketya.notificaciones.*} de application.yml (ver design.md D3, D4 y D7). */
@ConfigurationProperties(prefix = "ticketya.notificaciones")
@Getter
@Setter
public class NotificacionesProperties {

    private Procesador procesador = new Procesador();
    private Reintentos reintentos = new Reintentos();
    private Simulacion simulacion = new Simulacion();

    @Getter
    @Setter
    public static class Procesador {
        private boolean habilitado = true;
        private Duration intervalo = Duration.ofSeconds(1);
        private Duration reserva = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Reintentos {
        private int maxIntentos = 3;
        private Duration esperaInicial = Duration.ofSeconds(2);
        private double factor = 2;
    }

    @Getter
    @Setter
    public static class Simulacion {
        private List<String> emailsQueFallan = new ArrayList<>(List.of("falla@ticketya.test"));
        private List<String> emailsQueFallanUnaVez = new ArrayList<>(List.of("falla-una-vez@ticketya.test"));
    }
}
