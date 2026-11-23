package com.ticketya.notificaciones.service;

import com.ticketya.notificaciones.config.NotificacionesProperties;
import com.ticketya.notificaciones.model.Notificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Envia" el email escribiendolo en el log (design.md D7). Falla siempre para los emails de
 * {@code simulacion.emails-que-fallan} y solo en el primer intento para los de
 * {@code simulacion.emails-que-fallan-una-vez}. La decision depende solo del email y del numero de intento,
 * sin estado en memoria, para que las pruebas sean deterministas.
 */
@Component
public class EmailSimulado implements CanalEnvio {

    private static final Logger log = LoggerFactory.getLogger(EmailSimulado.class);

    private final Set<String> fallanSiempre;
    private final Set<String> fallanUnaVez;

    public EmailSimulado(NotificacionesProperties props) {
        this.fallanSiempre = normalizar(props.getSimulacion().getEmailsQueFallan());
        this.fallanUnaVez = normalizar(props.getSimulacion().getEmailsQueFallanUnaVez());
    }

    @Override
    public void enviar(Notificacion n, int intento) {
        String email = n.getDestinatario().email().toLowerCase(Locale.ROOT);
        if (fallanSiempre.contains(email)) {
            throw new EnvioFallidoException("Servidor de email simulado: rechazo el destinatario " + email);
        }
        if (fallanUnaVez.contains(email) && intento == 1) {
            throw new EnvioFallidoException("Servidor de email simulado: no disponible en el intento " + intento);
        }
        log.info("EMAIL SIMULADO enviado a {} (usuario {}): tipo={} datos={} correlationId={} intento={}",
                n.getDestinatario().email(), n.getDestinatario().usuarioId(), n.getTipo(), n.getDatos(),
                n.getCorrelationId(), intento);
    }

    private static Set<String> normalizar(java.util.List<String> emails) {
        return emails == null ? Set.of() : emails.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
