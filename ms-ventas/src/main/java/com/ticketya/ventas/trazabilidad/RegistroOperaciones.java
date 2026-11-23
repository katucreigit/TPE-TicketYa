package com.ticketya.ventas.trazabilidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registro de operaciones criticas (add-trazabilidad): una linea "OPERACION_CRITICA operacion=... usuario=...
 * correlationId=..." seguida de los ids de negocio en formato clave=valor.
 * <p>
 * Si hay una transaccion en curso, la linea se escribe recien despues de su commit: una operacion que se revierte
 * no queda registrada. El correlationId y el usuario se toman en el momento de la llamada.
 */
@Component
public class RegistroOperaciones {

    private static final Logger log = LoggerFactory.getLogger(RegistroOperaciones.class);

    /** Operaciones criticas de ms-ventas. */
    public enum Operacion {
        COMPRA_APROBADA,
        COMPRA_RECHAZADA,
        COMPRA_REEMBOLSADA,
        PAGO_INCIERTO,
        PAGO_RECONCILIADO,
        DEVOLUCION_SOLICITADA,
        DEVOLUCION_APROBADA,
        DEVOLUCION_RECHAZADA,
        REINTEGRO_COMPLETADO,
        CANCELACION_EVENTO,
        /** Reservada para la validacion de acceso QR (add-acceso-qr). */
        ENTRADA_VALIDADA
    }

    /** Resultado definitivo al resolver una compra en estado PAGO_INCIERTO. */
    public enum ResultadoReconciliacion {
        APROBADA,
        RECHAZADA,
        FALLIDA,
        REEMBOLSADA
    }

    public void registrar(Operacion operacion, Map<String, ?> datos) {
        String linea = linea(operacion, Correlacion.usuarioActual(), Correlacion.actual(), datos);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info(linea);
                }
            });
        } else {
            log.info(linea);
        }
    }

    /** Datos en el orden en que se pasan; los nulos se omiten. */
    public static Map<String, Object> datos(Object... claveValor) {
        if (claveValor.length % 2 != 0) {
            throw new IllegalArgumentException("Se esperaban pares clave, valor");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            if (claveValor[i + 1] != null) {
                datos.put((String) claveValor[i], claveValor[i + 1]);
            }
        }
        return datos;
    }

    private static String linea(Operacion operacion, String usuario, String correlationId, Map<String, ?> datos) {
        String negocio = datos.entrySet().stream()
                .map(e -> e.getKey() + "=" + Correlacion.limpiar(String.valueOf(e.getValue())))
                .collect(Collectors.joining(" "));
        return "OPERACION_CRITICA operacion=" + operacion
                + " usuario=" + usuario
                + " correlationId=" + correlationId
                + (negocio.isEmpty() ? "" : " " + negocio);
    }
}
