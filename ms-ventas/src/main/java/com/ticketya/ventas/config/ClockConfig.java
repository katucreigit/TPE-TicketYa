package com.ticketya.ventas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Reloj en UTC: las fechas de ms-reservas (expiraEn) se expresan en ese huso. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

