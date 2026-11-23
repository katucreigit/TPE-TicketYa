package com.ticketya.recomendaciones.dto;

import com.ticketya.recomendaciones.model.TipoEvento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cuerpo de PUT /preferencias. Todos los campos son opcionales: el ausente se guarda vacio. Un tipo inexistente
 * falla al deserializar y sale como 400 VALIDACION.
 */
public record PreferenciasRequest(
        List<@NotNull(message = "no puede ser nulo") TipoEvento> tipos,
        @Size(max = 20, message = "admite como maximo 20 artistas")
        List<@NotBlank(message = "no puede estar vacio") @Size(max = 100, message = "admite como maximo 100 caracteres") String> artistas,
        @Size(max = 100, message = "admite como maximo 100 caracteres")
        String ciudad
) {
}
