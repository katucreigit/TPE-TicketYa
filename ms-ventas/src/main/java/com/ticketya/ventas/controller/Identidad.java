package com.ticketya.ventas.controller;

import com.ticketya.ventas.exception.BadRequestException;
import com.ticketya.ventas.exception.ForbiddenException;

import java.util.ArrayList;
import java.util.List;

/**
 * Identidad de la Entrega 1 (sin JWT): headers X-User-Id y X-User-Rol que reenvia el gateway,
 * y validacion del header Idempotency-Key.
 */
final class Identidad {

    static final String HEADER_ID = "X-User-Id";
    static final String HEADER_ROL = "X-User-Rol";
    static final String HEADER_IDEMPOTENCY = "Idempotency-Key";
    private static final String CLIENTE = "CLIENTE";

    private Identidad() {
    }

    /** Devuelve el id del cliente si los headers estan presentes y el rol es CLIENTE. */
    static String cliente(String userId, String rol) {
        List<String> faltantes = new ArrayList<>();
        if (userId == null || userId.isBlank()) {
            faltantes.add("El header " + HEADER_ID + " es obligatorio");
        }
        if (rol == null || rol.isBlank()) {
            faltantes.add("El header " + HEADER_ROL + " es obligatorio");
        }
        if (!faltantes.isEmpty()) {
            throw new BadRequestException("VALIDACION", "Faltan los headers de identidad", faltantes);
        }
        if (!CLIENTE.equals(rol.trim())) {
            throw new ForbiddenException();
        }
        return userId.trim();
    }

    /** Valida que el header Idempotency-Key este presente y no sea blanco (400 IDEMPOTENCY_KEY_REQUERIDA). */
    static String idempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUERIDA",
                    "El header " + HEADER_IDEMPOTENCY + " es obligatorio");
        }
        return key.trim();
    }
}

