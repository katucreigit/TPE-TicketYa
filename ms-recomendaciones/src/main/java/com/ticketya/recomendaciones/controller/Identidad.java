package com.ticketya.recomendaciones.controller;

import com.ticketya.recomendaciones.exception.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Identidad por los headers X-User-Id y X-User-Rol que reenvia el gateway desde el JWT (design.md D7).
 * Sin headers responde 400 VALIDACION; con un rol no permitido, 403 ACCESO_DENEGADO.
 */
final class Identidad {

    static final String HEADER_ID = "X-User-Id";
    static final String HEADER_ROL = "X-User-Rol";
    private static final Set<String> ROLES = Set.of("CLIENTE", "ORGANIZADOR", "ADMIN", "PERSONAL_ACCESO");

    private Identidad() {
    }

    /** Preferencias y recomendaciones personales: solo CLIENTE. Devuelve el id del cliente. */
    static String cliente(String userId, String rol) {
        verificarPresentes(userId, rol);
        if (!"CLIENTE".equals(rol.trim())) {
            throw ApiException.accesoDenegado();
        }
        return userId.trim();
    }

    /** Consultas no personales ("tambien compraron"): cualquiera de los cuatro roles. */
    static void autenticado(String userId, String rol) {
        verificarPresentes(userId, rol);
        if (!ROLES.contains(rol.trim())) {
            throw ApiException.accesoDenegado();
        }
    }

    private static void verificarPresentes(String userId, String rol) {
        List<String> faltantes = new ArrayList<>();
        if (userId == null || userId.isBlank()) {
            faltantes.add("El header " + HEADER_ID + " es obligatorio");
        }
        if (rol == null || rol.isBlank()) {
            faltantes.add("El header " + HEADER_ROL + " es obligatorio");
        }
        if (!faltantes.isEmpty()) {
            throw ApiException.validacion("Faltan los headers de identidad", faltantes);
        }
    }
}
