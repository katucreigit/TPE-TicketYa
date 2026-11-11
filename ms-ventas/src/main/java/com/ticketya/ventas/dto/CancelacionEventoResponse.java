package com.ticketya.ventas.dto;

/**
 * Respuesta de POST /internal/eventos/{eventoId}/cancelacion. Los contadores son acumulados del evento, asi que
 * repetir la cancelacion devuelve los mismos totales (con los pendientes que se hayan completado al reintentar).
 */
public record CancelacionEventoResponse(Long eventoId,
                                        long entradasAfectadas,
                                        long reintegrosCompletados,
                                        long reintegrosPendientes) {
}
