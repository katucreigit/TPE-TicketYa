package com.ticketya.fila.dto;

import java.time.LocalDateTime;

/** Configuracion y tamaño actual de la fila de un evento (respuesta de la activacion). Fechas en UTC. */
public record FilaConfiguracion(
        Long eventoId,
        boolean activa,
        int tamanoTanda,
        int segundosEntreTandas,
        int minutosVentana,
        LocalDateTime proximaTandaEn,
        long enEspera,
        long habilitados
) {
}
