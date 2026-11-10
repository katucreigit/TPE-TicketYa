package com.ticketya.eventos.dto;

import java.util.List;

public record PageLugarResponse(
    List<LugarResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
