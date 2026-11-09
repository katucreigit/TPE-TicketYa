package com.ticketya.reservas.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periodico de ms-reservas (design.md, "Job de vencimiento").
 * Por ahora solo reintenta los avisos de agotado pendientes; el vencimiento de reservas se agrega en la tarea 2.9.
 */
@Component
public class VencimientoJob {

    private final AvisoAgotadoService avisoAgotadoService;

    public VencimientoJob(AvisoAgotadoService avisoAgotadoService) {
        this.avisoAgotadoService = avisoAgotadoService;
    }

    @Scheduled(fixedDelayString = "${ticketya.jobs.intervalo-ms:30000}",
            initialDelayString = "${ticketya.jobs.intervalo-ms:30000}")
    public void ejecutar() {
        avisoAgotadoService.reintentarPendientes();
    }
}
