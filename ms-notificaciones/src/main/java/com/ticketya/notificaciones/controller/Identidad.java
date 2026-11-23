package com.ticketya.notificaciones.controller;

import com.ticketya.notificaciones.exception.BadRequestException;
import com.ticketya.notificaciones.exception.ForbiddenException;

import java.util.ArrayList;
import java.util.List;

/**
 * Identidad que propaga el gateway en los headers X-User-Id y X-User-Rol (igual que en los demas servicios).
 * La consulta de notificaciones es solo para ADMIN.
 */
final class Identidad {

    static final String HEADER_ID = "X-User-Id";
    static final String HEADER_ROL = "X-User-Rol";
    private static final String ADMIN = "ADMIN";

    private Identidad() {
    }

    /** Devuelve el id del administrador si los headers estan presentes y el rol es ADMIN. */
    static String admin(String userId, String rol) {
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
        if (!ADMIN.equals(rol.trim())) {
            throw new ForbiddenException();
        }
        return userId.trim();
    }

    /** Valida page >= 0 y 1 <= size <= 100 segun openapi.yaml. */
    static void validarPaginacion(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("VALIDACION", "Parametros de paginacion invalidos");
        }
    }
}
