package com.ticketya.reservas.repository.projection;

import com.ticketya.reservas.model.enums.EstadoAsiento;

/** Asiento del mapa: solo fila, numero y estado. */
public interface AsientoView {

    int getFila();

    int getNumero();

    EstadoAsiento getEstado();
}
