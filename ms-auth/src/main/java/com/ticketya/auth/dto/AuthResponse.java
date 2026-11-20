package com.ticketya.auth.dto;

public record AuthResponse(
        String token,
        Long userId,
        String rol
) {}
