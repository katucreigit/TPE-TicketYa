package com.ticketya.fila.trazabilidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registro de operaciones criticas (add-trazabilidad): una linea "OPERACION_CRITICA operacion=... usuario=...
 * correlationId=..." seguida de los ids de negocio en formato clave=valor.
 * <p>
 * A diferencia de ms-reservas no hay transacciones de base: cada operacion de la fila es un script de Redis que ya
 * se aplico cuando se llama a este registro, asi que la linea se escribe en el momento (design.md D12).
 */
@Component
public class RegistroOperaciones {

    private static final Logger log = LoggerFactory.getLogger(RegistroOperaciones.class);

    /** Operaciones criticas de ms-fila. */
    public enum Operacion {
        FILA_ACTIVADA,
        FILA_DESACTIVADA,
        INGRESO_FILA,
        CLIENTE_HABILITADO,
        TURNO_VENCIDO
    }

    public void registrar(Operacion operacion, Map<String, ?> datos) {
        log.info(linea(operacion, Correlacion.usuarioActual(), Correlacion.actual(), datos));
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
