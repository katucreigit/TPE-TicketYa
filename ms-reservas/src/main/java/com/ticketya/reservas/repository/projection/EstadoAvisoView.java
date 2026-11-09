package com.ticketya.reservas.repository.projection;

/** Estado de un evento para decidir el aviso de agotado. */
public interface EstadoAvisoView {

    Boolean getAbierto();

    /** Null: todavia no se notifico nada. */
    Boolean getAgotadoNotificado();

    Long getLibres();
}
