package com.ticketya.ventas.trazabilidad;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captura en memoria los eventos de log de las clases indicadas (Logback ListAppender), con su mensaje ya
 * formateado y su MDC. Se usa con try-with-resources o abriendo en @BeforeEach y cerrando en @AfterEach.
 */
public final class CapturaLogs implements AutoCloseable {

    private static final Pattern CLAVE_VALOR = Pattern.compile("(\\w+)=(\\S*)");

    private final List<Logger> loggers;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public CapturaLogs(Class<?>... clases) {
        appender.start();
        loggers = java.util.Arrays.stream(clases)
                .map(c -> (Logger) LoggerFactory.getLogger(c))
                .toList();
        loggers.forEach(l -> l.addAppender(appender));
    }

    /** Mensajes formateados que empiezan con el prefijo (por ejemplo "HTTP_REQUEST" u "OPERACION_CRITICA"). */
    public List<String> mensajes(String prefijo) {
        return eventos(prefijo).stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    public List<ILoggingEvent> eventos(String prefijo) {
        synchronized (appender.list) {
            return appender.list.stream().filter(e -> e.getFormattedMessage().startsWith(prefijo)).toList();
        }
    }

    /** Pares clave=valor de cada linea con el prefijo. */
    public List<Map<String, String>> campos(String prefijo) {
        return mensajes(prefijo).stream().map(CapturaLogs::parsear).toList();
    }

    /** Lineas OPERACION_CRITICA de una operacion, como pares clave=valor. */
    public List<Map<String, String>> operaciones(String operacion) {
        return campos("OPERACION_CRITICA").stream().filter(c -> operacion.equals(c.get("operacion"))).toList();
    }

    public void limpiar() {
        synchronized (appender.list) {
            appender.list.clear();
        }
    }

    static Map<String, String> parsear(String linea) {
        Map<String, String> campos = new java.util.LinkedHashMap<>();
        Matcher m = CLAVE_VALOR.matcher(linea);
        while (m.find()) {
            campos.put(m.group(1), m.group(2));
        }
        return campos;
    }

    @Override
    public void close() {
        loggers.forEach(l -> l.detachAppender(appender));
        appender.stop();
    }
}
