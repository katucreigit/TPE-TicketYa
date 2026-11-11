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
    private static final String ADMIN = "ADMIN";

    record Solicitante(String userId, boolean esAdmin) {
    }

    private Identidad() {
    }

    /** Devuelve el id del cliente si los headers estan presentes y el rol es CLIENTE. */
    static String cliente(String userId, String rol) {
        Solicitante s = validarHeaders(userId, rol);
        if (s.esAdmin() || !CLIENTE.equals(rol.trim())) {
            throw new ForbiddenException();
        }
        return s.userId();
    }

    /** Devuelve el id del administrador si los headers estan presentes y el rol es ADMIN. */
    static String admin(String userId, String rol) {
        Solicitante s = validarHeaders(userId, rol);
        if (!s.esAdmin()) {
            throw new ForbiddenException();
        }
        return s.userId();
    }

    /** Permite rol CLIENTE o ADMIN (para consultar el detalle de una compra o entrada). */
    static Solicitante clienteOAdmin(String userId, String rol) {
        return validarHeaders(userId, rol);
    }

    private static Solicitante validarHeaders(String userId, String rol) {
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
        String rolLimpio = rol.trim();
        if (CLIENTE.equals(rolLimpio)) {
            return new Solicitante(userId.trim(), false);
        }
        if (ADMIN.equals(rolLimpio)) {
            return new Solicitante(userId.trim(), true);
        }
        throw new ForbiddenException();
    }

    /** Valida que el header Idempotency-Key este presente y no sea blanco (400 IDEMPOTENCY_KEY_REQUERIDA). */
    static String idempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUERIDA",
                    "El header " + HEADER_IDEMPOTENCY + " es obligatorio");
        }
        return key.trim();
    }

    /** Valida page >= 0 y 1 <= size <= 100 segun openapi.yaml. */
    static void validarPaginacion(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("VALIDACION", "Parametros de paginacion invalidos");
        }
    }
}
