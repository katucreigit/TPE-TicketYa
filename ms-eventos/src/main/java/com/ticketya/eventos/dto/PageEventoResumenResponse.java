package com.ticketya.eventos.dto;

import java.util.List;

public record PageEventoResumenResponse(
        List<EventoResumenResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
