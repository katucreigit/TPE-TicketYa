package com.ticketya.reservas.trazabilidad;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Identificador de correlacion (add-trazabilidad): nombre del header, claves del MDC y utilidades.
 * Cada solicitud HTTP y cada corrida de un proceso programado tiene un correlationId en el MDC;
 * los registros y las llamadas salientes lo toman de ahi.
 */
public final class Correlacion {

    public static final String HEADER = "X-Correlation-Id";
    public static final String HEADER_USUARIO = "X-User-Id";
    public static final String HEADER_ROL = "X-User-Rol";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_USUARIO = "usuario";
    public static final String MDC_ROL = "rol";

    /** Usuario de las operaciones que no llegan con X-User-Id (procesos programados y llamadas internas). */
    public static final String SISTEMA = "SISTEMA";

    private static final int LARGO_MAXIMO = 128;

    private Correlacion() {
    }

    /** El correlationId de la solicitud o proceso en curso, o null si no hay ninguno. */
    public static String actual() {
        return MDC.get(MDC_CORRELATION_ID);
    }

    /** El usuario de la solicitud en curso (X-User-Id), o {@link #SISTEMA} si no vino. */
    public static String usuarioActual() {
        String usuario = MDC.get(MDC_USUARIO);
        return usuario == null ? SISTEMA : usuario;
    }

    public static String nuevo() {
        return UUID.randomUUID().toString();
    }

    /**
     * Conserva el valor recibido si es usable en un log (no vacio, sin caracteres de control y de largo
     * razonable); si no, devuelve null para que se genere uno nuevo.
     */
    public static String normalizar(String recibido) {
        if (recibido == null) {
            return null;
        }
        String valor = recibido.trim();
        if (valor.isEmpty() || valor.length() > LARGO_MAXIMO || valor.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return valor;
    }

    /** Valor de un header apto para el log: sin saltos de linea ni caracteres de control, y acotado. */
    public static String limpiar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\p{Cntrl}", "_");
        return limpio.length() > LARGO_MAXIMO ? limpio.substring(0, LARGO_MAXIMO) : limpio;
    }

    /**
     * Ejecuta un proceso sin solicitud HTTP (por ejemplo, el job de vencimiento) con un correlationId propio, salvo
     * que ya haya uno en curso. Lo quita al terminar solo si lo puso.
     */
    public static <T> T enProceso(Supplier<T> proceso) {
        if (actual() != null) {
            return proceso.get();
        }
        MDC.put(MDC_CORRELATION_ID, nuevo());
        try {
            return proceso.get();
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    public static void enProceso(Runnable proceso) {
        enProceso(() -> {
            proceso.run();
            return null;
        });
    }
}
