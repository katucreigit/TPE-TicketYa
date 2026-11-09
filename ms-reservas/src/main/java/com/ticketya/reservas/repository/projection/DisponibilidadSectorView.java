package com.ticketya.reservas.repository.projection;

/** Fila de la consulta de disponibilidad: lugares libres de un sector calculados en la base. */
public interface DisponibilidadSectorView {

    Long getSectorId();

    String getTipo();

    Integer getCapacidad();

    Long getDisponibles();
}
