package com.ticketya.recomendaciones;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que las pruebas fijan y adelantan, para vencer la copia del catalogo sin esperar. */
public class RelojControlable extends Clock {

    private volatile Instant ahora = Instant.parse("2026-12-01T20:00:00Z");

    public void fijar(Instant instante) {
        this.ahora = instante;
    }

    public void avanzar(Duration duracion) {
        this.ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable();
        }
    }
}
