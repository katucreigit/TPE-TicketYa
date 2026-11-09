package com.ticketya.reservas.controller;

import com.ticketya.reservas.exception.CodigoError;

import java.util.ArrayList;
import java.util.List;

/**
 * Identidad de la Entrega 1 (sin JWT): headers X-User-Id y X-User-Rol que reenvia el gateway.
 * Sin headers responde 400 VALIDACION; con un rol distinto de CLIENTE, 403 ACCESO_DENEGADO.
 */
final class Identidad {

    static final String HEADER_ID = "X-User-Id";
    static final String HEADER_ROL = "X-User-Rol";
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
            throw CodigoError.validacion("Faltan los headers de identidad", faltantes);
        }
        if (!CLIENTE.equals(rol.trim())) {
            throw CodigoError.accesoDenegado();
        }
        return userId.trim();
    }
}
