package com.ticketya.eventos.dto;

import com.ticketya.eventos.model.enums.TipoEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record EventoRequest(
        @NotBlank(message = "El nombre del evento es obligatorio")
        String nombre,

        String descripcion,

        @NotNull(message = "El tipo de evento es obligatorio")
        TipoEvento tipo,

        @NotNull(message = "El lugar es obligatorio")
        Long lugarId,

        @NotNull(message = "La fecha del evento es obligatoria")
        @Future(message = "La fecha del evento debe ser futura")
        LocalDateTime fechaHora,

        List<@Valid ParticipanteRequest> participantes,

        @Min(value = 1, message = "El máximo de entradas debe ser al menos 1")
        Integer maxEntradasPorCliente,

        Boolean altaDemanda
) {}
