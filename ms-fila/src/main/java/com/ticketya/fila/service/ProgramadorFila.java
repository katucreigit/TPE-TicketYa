package com.ticketya.fila.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el proceso de la fila cada {@code ticketya.fila.procesador.intervalo}. Se apaga con
 * {@code procesador.habilitado=false}: las pruebas lo apagan y llaman a {@link ProcesadorFila} con el reloj controlado.
 */
@Component
@ConditionalOnProperty(prefix = "ticketya.fila.procesador", name = "habilitado",
        havingValue = "true", matchIfMissing = true)
public class ProgramadorFila {

    private static final Logger log = LoggerFactory.getLogger(ProgramadorFila.class);

    private final ProcesadorFila procesador;

    public ProgramadorFila(ProcesadorFila procesador) {
        this.procesador = procesador;
    }

    @Scheduled(fixedDelayString = "${ticketya.fila.procesador.intervalo:1s}")
    public void ejecutar() {
        try {
            procesador.procesar();
        } catch (RuntimeException e) {
            // Por ejemplo, Redis no disponible al leer las filas activas: se reintenta en el proximo ciclo.
            log.warn("No se pudieron procesar las filas activas: {}", e.getMessage());
        }
    }
}
