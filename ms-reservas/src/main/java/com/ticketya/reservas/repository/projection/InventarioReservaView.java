package com.ticketya.reservas.repository.projection;

/** Datos del inventario del evento que necesita una reserva, leidos con FOR SHARE. */
public interface InventarioReservaView {

    Boolean getAbierto();

    Integer getMaxPorCliente();
}
