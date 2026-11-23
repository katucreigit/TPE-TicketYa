package com.ticketya.fila.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * Reloj en UTC. Los scripts de Redis reciben la hora como argumento (design.md D6), asi que las pruebas lo
     * reemplazan por uno controlable para atravesar tandas y ventanas sin esperar.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
