package com.ticketya.fila.dto;

/** Situacion de un cliente respecto de la fila de un evento. */
public enum EstadoFilaCliente {
    /** Esta en la fila esperando su tanda; tiene posicion. */
    EN_ESPERA,
    /** Puede reservar hasta habilitadoHasta. */
    HABILITADO,
    /** La fila esta activa y el cliente no esta en ella (nunca ingreso o perdio el turno). */
    FUERA_DE_FILA,
    /** El evento no tiene la fila activa: cualquiera puede reservar. */
    SIN_FILA
}
