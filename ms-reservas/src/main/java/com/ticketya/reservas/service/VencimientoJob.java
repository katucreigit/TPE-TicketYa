package com.ticketya.reservas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periodico de ms-reservas (design.md, "Job de vencimiento"): vence las reservas PENDIENTE expiradas
 * por lotes (cada lote en su propia transaccion, con FOR UPDATE SKIP LOCKED) y despues reintenta los avisos
 * de agotado pendientes, que tambien cubren los cambios de disponibilidad que produjo el vencimiento.
 * Los tests invocan {@link #ejecutar()} directamente.
 */
@Component
public class VencimientoJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoJob.class);

    private final ReservaService reservaService;
    private final AvisoAgotadoService avisoAgotadoService;

    public VencimientoJob(ReservaService reservaService, AvisoAgotadoService avisoAgotadoService) {
        this.reservaService = reservaService;
        this.avisoAgotadoService = avisoAgotadoService;
    }

    @Scheduled(fixedDelayString = "${ticketya.jobs.intervalo-ms:30000}",
            initialDelayString = "${ticketya.jobs.intervalo-ms:30000}")
    public void ejecutar() {
        try {
            boolean quedan;
            do {
                quedan = reservaService.vencerLote();
            } while (quedan);
        } catch (RuntimeException ex) {
            // El lote fallido se revirtio completo; lo retoma la proxima corrida.
            log.warn("Fallo el vencimiento de reservas; se reintentara en la proxima corrida", ex);
        }
        avisoAgotadoService.reintentarPendientes();
    }
}
