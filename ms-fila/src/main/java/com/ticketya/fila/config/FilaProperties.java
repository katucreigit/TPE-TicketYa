package com.ticketya.fila.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Propiedades {@code ticketya.fila.*} de application.yml (design.md D2, D5 y D7). */
@ConfigurationProperties(prefix = "ticketya.fila")
@Getter
@Setter
public class FilaProperties {

    /** Ventana de compra de cada habilitado; se copia a la fila al activarla. */
    private int minutosVentana = 10;
    private int tamanoTandaDefault = 100;
    private int segundosEntreTandasDefault = 60;
    /** TTL de seguridad de las claves de cada fila; se renueva al activar y en cada tanda. */
    private int ttlHoras = 24;
    private Procesador procesador = new Procesador();

    @Getter
    @Setter
    public static class Procesador {
        private boolean habilitado = true;
        private Duration intervalo = Duration.ofSeconds(1);
    }
}
