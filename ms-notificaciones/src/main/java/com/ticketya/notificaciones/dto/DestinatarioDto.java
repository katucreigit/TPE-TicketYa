package com.ticketya.notificaciones.dto;

import com.ticketya.notificaciones.model.Destinatario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Destinatario del pedido y de la respuesta (schema {@code Destinatario} de openapi.yaml). */
public record DestinatarioDto(
        @NotBlank String usuarioId,
        @NotBlank @Email String email
) {
    public static DestinatarioDto from(Destinatario d) {
        return d == null ? null : new DestinatarioDto(d.usuarioId(), d.email());
    }

    public Destinatario toModel() {
        return new Destinatario(usuarioId.trim(), email.trim());
    }
}
