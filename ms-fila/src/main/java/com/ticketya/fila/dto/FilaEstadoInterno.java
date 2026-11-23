package com.ticketya.fila.dto;

/** Respuesta de la consulta interna que hace ms-reservas antes de crear una reserva. */
public record FilaEstadoInterno(boolean activa, boolean habilitado) {
}
