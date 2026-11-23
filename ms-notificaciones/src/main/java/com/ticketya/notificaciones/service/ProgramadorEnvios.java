package com.ticketya.notificaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el procesador cada {@code ticketya.notificaciones.procesador.intervalo}. Se puede apagar con
 * {@code procesador.habilitado=false} (las pruebas de reintentos lo apagan para controlar el reloj).
 */
@Component
@ConditionalOnProperty(prefix = "ticketya.notificaciones.procesador", name = "habilitado",
        havingValue = "true", matchIfMissing = true)
public class ProgramadorEnvios {

    private static final Logger log = LoggerFactory.getLogger(ProgramadorEnvios.class);

    private final ProcesadorNotificaciones procesador;

    public ProgramadorEnvios(ProcesadorNotificaciones procesador) {
        this.procesador = procesador;
    }

    @Scheduled(fixedDelayString = "${ticketya.notificaciones.procesador.intervalo:1s}")
    public void ejecutar() {
        try {
            procesador.procesarPendientes();
        } catch (RuntimeException e) {
            // Por ejemplo, MongoDB no disponible: se reintenta en el proximo ciclo.
            log.warn("No se pudieron procesar las notificaciones pendientes: {}", e.getMessage());
        }
    }
}
