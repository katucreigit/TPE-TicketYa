package com.ticketya.fila.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Respuesta de ingresar y de consultar la posicion. {@code posicion} solo en EN_ESPERA y {@code habilitadoHasta}
 * (UTC) solo en HABILITADO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilaEstadoCliente(
        Long eventoId,
        boolean activa,
        EstadoFilaCliente estado,
        Long posicion,
        boolean habilitado,
        LocalDateTime habilitadoHasta
) {
    public static FilaEstadoCliente sinFila(Long eventoId) {
        return new FilaEstadoCliente(eventoId, false, EstadoFilaCliente.SIN_FILA, null, true, null);
    }

    public static FilaEstadoCliente enEspera(Long eventoId, long posicion) {
        return new FilaEstadoCliente(eventoId, true, EstadoFilaCliente.EN_ESPERA, posicion, false, null);
    }

    public static FilaEstadoCliente habilitado(Long eventoId, LocalDateTime hasta) {
        return new FilaEstadoCliente(eventoId, true, EstadoFilaCliente.HABILITADO, null, true, hasta);
    }

    public static FilaEstadoCliente fueraDeFila(Long eventoId) {
        return new FilaEstadoCliente(eventoId, true, EstadoFilaCliente.FUERA_DE_FILA, null, false, null);
    }
}
