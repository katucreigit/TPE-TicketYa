package com.ticketya.notificaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Reloj en UTC; las pruebas lo reemplazan para controlar las esperas de los reintentos. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
