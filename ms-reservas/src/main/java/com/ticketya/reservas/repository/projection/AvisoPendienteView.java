package com.ticketya.reservas.repository.projection;

/** Evento abierto cuyo estado real de agotado difiere del ultimo aviso confirmado. */
public interface AvisoPendienteView {

    Long getEventoId();

    Boolean getAgotado();
}
