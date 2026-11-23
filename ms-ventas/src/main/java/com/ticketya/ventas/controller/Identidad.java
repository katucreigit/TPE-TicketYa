package com.ticketya.ventas.controller;

import com.ticketya.ventas.exception.BadRequestException;
import com.ticketya.ventas.exception.ForbiddenException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Identidad de la Entrega 1 (sin JWT): headers X-User-Id y X-User-Rol que reenvia el gateway,
 * y validacion del header Idempotency-Key.
 */
final class Identidad {

    static final String HEADER_ID = "X-User-Id";
    static final String HEADER_ROL = "X-User-Rol";
    static final String HEADER_EMAIL = "X-User-Email";
    static final String HEADER_IDEMPOTENCY = "Idempotency-Key";
    private static final String CLIENTE = "CLIENTE";
    private static final String ADMIN = "ADMIN";
    private static final String PERSONAL_ACCESO = "PERSONAL_ACCESO";
    private static final Pattern EMAIL_BASICO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private record Headers(String userId, String rol) {
    }

    record Solicitante(String userId, boolean esAdmin) {
    }

    private Identidad() {
    }

    /** Devuelve el id del cliente si los headers estan presentes y el rol es CLIENTE. */
    static String cliente(String userId, String rol) {
        Headers h = exigirHeaders(userId, rol);
        if (!CLIENTE.equals(h.rol())) {
            throw new ForbiddenException();
        }
        return h.userId();
    }

    /** Devuelve el id del administrador si los headers estan presentes y el rol es ADMIN. */
    static String admin(String userId, String rol) {
        Headers h = exigirHeaders(userId, rol);
        if (!ADMIN.equals(h.rol())) {
            throw new ForbiddenException();
        }
        return h.userId();
    }

    /** Devuelve el id del operador si los headers estan presentes y el rol es PERSONAL_ACCESO. */
    static String personalAcceso(String userId, String rol) {
        Headers h = exigirHeaders(userId, rol);
        if (!PERSONAL_ACCESO.equals(h.rol())) {
            throw new ForbiddenException();
        }
        return h.userId();
    }

    /** Permite rol CLIENTE o ADMIN (para consultar el detalle de una compra o entrada). */
    static Solicitante clienteOAdmin(String userId, String rol) {
        Headers h = exigirHeaders(userId, rol);
        if (CLIENTE.equals(h.rol())) {
            return new Solicitante(h.userId(), false);
        }
        if (ADMIN.equals(h.rol())) {
            return new Solicitante(h.userId(), true);
        }
        throw new ForbiddenException();
    }

    private static Headers exigirHeaders(String userId, String rol) {
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
        return new Headers(userId.trim(), rol.trim());
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

    /** Normaliza el header opcional X-User-Email; devuelve {@code null} si falta o no tiene formato valido. */
    static String emailOpcional(String email) {
        if (email == null) {
            return null;
        }
        String limpio = email.trim();
        if (limpio.isEmpty() || limpio.length() > 254 || limpio.contains("..")) {
            return null;
        }
        return EMAIL_BASICO.matcher(limpio).matches() ? limpio : null;
    }
}
