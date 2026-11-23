package com.ticketya.notificaciones.model;

/** Subdocumento con el usuario al que se notifica y su email. */
public record Destinatario(String usuarioId, String email) {
}
