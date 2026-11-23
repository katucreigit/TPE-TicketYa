package com.ticketya.ventas.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cliente HTTP best-effort hacia {@code ms-notificaciones} ({@code POST /internal/notificaciones}).
 * Centraliza el aislamiento de fallos: nunca lanza excepciones ni interrumpe la operacion principal.
 */
@Component
public class NotificacionesClient {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesClient.class);
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String DOMINIO_DEFECTO = "ticketya.local";
    private static final int MAX_CLAVE_DEDUPLICACION = 200;
    private static final int MAX_LOCAL_EMAIL = 64;
    private static final int MAX_EMAIL = 254;
    private static final Pattern EMAIL_BASICO =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public enum TipoNotificacion {
        COMPRA_CONFIRMADA,
        EVENTO_CANCELADO
    }

    public record Destinatario(String usuarioId, String email) {
    }

    public record NotificacionRequest(
            TipoNotificacion tipo,
            Destinatario destinatario,
            Map<String, Object> datos,
            String claveDeduplicacion) {
    }

    private final RestClient restClient;
    private final String emailDominio;

    @Autowired
    public NotificacionesClient(
            RestClient.Builder builder,
            @Value("${ticketya.services.notificaciones-url}") String notificacionesUrl,
            @Value("${ticketya.services.notificaciones-connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${ticketya.services.notificaciones-read-timeout-ms:2000}") int readTimeoutMs,
            @Value("${ticketya.services.notificaciones-email-dominio:ticketya.local}") String emailDominio) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.baseUrl(notificacionesUrl).requestFactory(requestFactory).build();
        this.emailDominio = normalizarDominio(emailDominio);
    }

    /** Para pruebas: permite inyectar un {@link RestClient} ya configurado. */
    public NotificacionesClient(RestClient restClient, String emailDominio) {
        this.restClient = restClient;
        this.emailDominio = normalizarDominio(emailDominio);
    }

    /** Para pruebas con dominio por defecto ({@code ticketya.local}). */
    public NotificacionesClient(RestClient restClient) {
        this(restClient, DOMINIO_DEFECTO);
    }

    /**
     * Envia una notificacion en modo best-effort. Nunca propaga excepciones ante errores HTTP,
     * timeouts o fallos de red; ante cualquier problema registra {@code WARN} y retorna normalmente.
     */
    public void enviar(TipoNotificacion tipo,
                       String usuarioId,
                       String emailExplicito,
                       Map<String, Object> datos,
                       String claveDeduplicacion) {
        String clave = acortarClave(claveDeduplicacion);
        String email = resolverEmail(emailExplicito, usuarioId);
        NotificacionRequest request = new NotificacionRequest(
                tipo,
                new Destinatario(usuarioId, email),
                datos,
                clave
        );

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri("/internal/notificaciones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request);

            String correlationId = MDC.get(MDC_CORRELATION_ID);
            if (correlationId != null && !correlationId.isBlank()) {
                spec = spec.header(HEADER_CORRELATION_ID, correlationId.trim());
            }

            ResponseEntity<Void> response = spec.retrieve().toBodilessEntity();
            if (!HttpStatus.ACCEPTED.equals(response.getStatusCode())) {
                log.warn("ms-notificaciones respondio estado inesperado {} para tipo={} usuarioId={} clave={}",
                        response.getStatusCode().value(), tipo, usuarioId, clave);
            }
        } catch (RestClientResponseException ex) {
            log.warn("ms-notificaciones rechazo la notificacion tipo={} usuarioId={} clave={} status={}: {}",
                    tipo, usuarioId, clave, ex.getStatusCode().value(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar notificacion tipo={} usuarioId={} clave={}: {}",
                    tipo, usuarioId, clave, ex.getMessage());
        }
    }

    private String resolverEmail(String emailExplicito, String usuarioId) {
        if (esEmailValido(emailExplicito)) {
            return emailExplicito.trim();
        }
        if (esEmailValido(usuarioId)) {
            return usuarioId.trim();
        }
        String local = sanitizarParteLocal(usuarioId);
        return local + "@" + emailDominio;
    }

    private static boolean esEmailValido(String valor) {
        if (valor == null) {
            return false;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty() || limpio.length() > MAX_EMAIL || limpio.contains("..")) {
            return false;
        }
        return EMAIL_BASICO.matcher(limpio).matches();
    }

    private static String sanitizarParteLocal(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return "usuario";
        }
        String sanitized = usuarioId.trim()
                .replaceAll("[^a-zA-Z0-9._-]", "-")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^\\.+|\\.+$", "");
        if (sanitized.length() > MAX_LOCAL_EMAIL) {
            sanitized = sanitized.substring(0, MAX_LOCAL_EMAIL).replaceAll("\\.+$", "");
        }
        return sanitized.isEmpty() ? "usuario" : sanitized;
    }

    private static String acortarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        String limpia = clave.trim();
        return limpia.length() <= MAX_CLAVE_DEDUPLICACION
                ? limpia
                : limpia.substring(0, MAX_CLAVE_DEDUPLICACION);
    }

    private static String normalizarDominio(String dominio) {
        if (dominio == null || dominio.isBlank()) {
            return DOMINIO_DEFECTO;
        }
        return dominio.trim();
    }
}
