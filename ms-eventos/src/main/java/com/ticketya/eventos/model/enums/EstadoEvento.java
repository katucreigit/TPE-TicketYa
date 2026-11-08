package com.ticketya.eventos.model.enums;

/**
 * SUSPENDIDO es reversible (vuelve a PUBLICADO al reanudar). Ver arquitectura.md, decision 1.
 */
public enum EstadoEvento {
    BORRADOR, PUBLICADO, AGOTADO, SUSPENDIDO, CANCELADO, FINALIZADO
}
