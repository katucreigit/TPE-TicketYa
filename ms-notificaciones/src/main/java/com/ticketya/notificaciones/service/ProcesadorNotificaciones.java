package com.ticketya.notificaciones.service;

import com.ticketya.notificaciones.config.NotificacionesProperties;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Envia las notificaciones PENDIENTE cuyo proximo intento ya vencio y aplica los reintentos (design.md D3 y D4).
 * Todo el estado (intentos, espera, error) vive en el documento, asi que un reinicio no pierde nada.
 */
@Service
public class ProcesadorNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorNotificaciones.class);
    /** Maximo de notificaciones por ciclo, para no acaparar el hilo del scheduler. */
    static final int LOTE = 100;
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final MongoTemplate mongo;
    private final CanalEnvio canal;
    private final Clock clock;
    private final NotificacionesProperties props;

    public ProcesadorNotificaciones(MongoTemplate mongo, CanalEnvio canal, Clock clock, NotificacionesProperties props) {
        this.mongo = mongo;
        this.canal = canal;
        this.clock = clock;
        this.props = props;
    }

    /** Procesa hasta {@link #LOTE} notificaciones vencidas y devuelve cuantas proceso. */
    public int procesarPendientes() {
        int procesadas = 0;
        while (procesadas < LOTE) {
            Optional<Notificacion> siguiente = tomarSiguiente();
            if (siguiente.isEmpty()) {
                break;
            }
            procesar(siguiente.get());
            procesadas++;
        }
        return procesadas;
    }

    /**
     * Toma de forma atomica una notificacion vencida: corre su proximo intento al final de la reserva, de modo
     * que ningun otro procesador la vea hasta que termine este (o hasta que venza la reserva, si este se cae).
     */
    public Optional<Notificacion> tomarSiguiente() {
        Instant ahora = clock.instant();
        Query query = new Query(where("estado").is(EstadoNotificacion.PENDIENTE).and("proximoIntentoEn").lte(ahora))
                .with(Sort.by(Sort.Order.asc("proximoIntentoEn")));
        Update update = new Update()
                .set("proximoIntentoEn", ahora.plus(props.getProcesador().getReserva()))
                .set("actualizadaEn", ahora);
        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Notificacion.class));
    }

    /** Hace un intento de envio de una notificacion ya tomada y guarda el resultado. */
    public void procesar(Notificacion n) {
        int intento = n.getIntentos() + 1;
        MDC.put(MDC_CORRELATION_ID, n.getCorrelationId() == null ? "" : n.getCorrelationId());
        try {
            canal.enviar(n, intento);
            Instant ahora = clock.instant();
            actualizar(n, new Update()
                    .set("estado", EstadoNotificacion.ENVIADA)
                    .set("intentos", intento)
                    .set("enviadaEn", ahora)
                    .set("actualizadaEn", ahora)
                    .unset("proximoIntentoEn"));
        } catch (RuntimeException e) {
            registrarFallo(n, intento, e);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    /** Espera antes del intento siguiente al intento {@code intento}: esperaInicial x factor^(intento-1). */
    public Duration esperaTrasIntento(int intento) {
        NotificacionesProperties.Reintentos r = props.getReintentos();
        double millis = r.getEsperaInicial().toMillis() * Math.pow(r.getFactor(), intento - 1);
        return Duration.ofMillis(Math.round(millis));
    }

    private void registrarFallo(Notificacion n, int intento, RuntimeException e) {
        Instant ahora = clock.instant();
        String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        Update update = new Update()
                .set("intentos", intento)
                .set("ultimoError", error)
                .set("actualizadaEn", ahora);
        if (intento >= props.getReintentos().getMaxIntentos()) {
            update.set("estado", EstadoNotificacion.FALLIDA).unset("proximoIntentoEn");
            log.warn("Notificacion {} FALLIDA tras {} intentos: {}", n.getId(), intento, error);
        } else {
            Duration espera = esperaTrasIntento(intento);
            update.set("proximoIntentoEn", ahora.plus(espera));
            log.warn("Notificacion {} fallo el intento {} ({}); reintento en {} ms",
                    n.getId(), intento, error, espera.toMillis());
        }
        actualizar(n, update);
    }

    private void actualizar(Notificacion n, Update update) {
        mongo.updateFirst(new Query(where("_id").is(n.getId()).and("estado").is(EstadoNotificacion.PENDIENTE)),
                update, Notificacion.class);
    }
}
