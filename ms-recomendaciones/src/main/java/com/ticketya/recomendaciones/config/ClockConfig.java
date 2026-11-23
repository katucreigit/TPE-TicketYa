package com.ticketya.recomendaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Reloj en UTC; las pruebas lo reemplazan para controlar el vencimiento de la copia del catalogo. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
