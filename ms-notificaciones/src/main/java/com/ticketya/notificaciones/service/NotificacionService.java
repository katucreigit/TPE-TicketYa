package com.ticketya.notificaciones.service;

import com.ticketya.notificaciones.dto.NotificacionRequest;
import com.ticketya.notificaciones.exception.NotFoundException;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import com.ticketya.notificaciones.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

/** Recepcion, deduplicacion y consulta de notificaciones. El envio lo hace {@link ProcesadorNotificaciones}. */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final MongoTemplate mongo;
    private final NotificacionRepository repository;
    private final Clock clock;

    public NotificacionService(MongoTemplate mongo, NotificacionRepository repository, Clock clock) {
        this.mongo = mongo;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Guarda la notificacion PENDIENTE con el proximo intento ya vencido, para que el procesador la tome en su
     * proximo ciclo. Si la clave de deduplicacion ya existe, devuelve la notificacion existente (design.md D5):
     * se inserta directamente y el indice unico decide, sin consultar antes, para no tener una carrera.
     */
    public Notificacion recibir(NotificacionRequest req, String correlationId) {
        Instant ahora = clock.instant();
        Notificacion n = new Notificacion();
        n.setTipo(req.tipo());
        n.setDestinatario(req.destinatario().toModel());
        n.setDatos(req.datos() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(req.datos()));
        n.setEstado(EstadoNotificacion.PENDIENTE);
        n.setIntentos(0);
        n.setClaveDeduplicacion(normalizarClave(req.claveDeduplicacion()));
        n.setCorrelationId(correlationId);
        n.setCreadaEn(ahora);
        n.setActualizadaEn(ahora);
        n.setProximoIntentoEn(ahora);
        try {
            Notificacion guardada = mongo.insert(n);
            log.info("Notificacion {} aceptada: tipo={} usuario={}", guardada.getId(), guardada.getTipo(),
                    guardada.getDestinatario().usuarioId());
            return guardada;
        } catch (DuplicateKeyException e) {
            if (n.getClaveDeduplicacion() == null) {
                throw e;
            }
            Notificacion existente = repository.findByClaveDeduplicacion(n.getClaveDeduplicacion()).orElseThrow(() -> e);
            log.info("Notificacion repetida con clave {}: se devuelve la existente {}",
                    n.getClaveDeduplicacion(), existente.getId());
            return existente;
        }
    }

    public Notificacion obtener(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la notificacion " + id));
    }

    /** Listado para administradores: filtros combinables y orden de la mas nueva a la mas vieja. */
    public Page<Notificacion> buscar(String usuarioId, TipoNotificacion tipo, EstadoNotificacion estado,
                                     int page, int size) {
        Criteria criteria = new Criteria();
        if (usuarioId != null && !usuarioId.isBlank()) {
            criteria = criteria.and("destinatario.usuarioId").is(usuarioId.trim());
        }
        if (tipo != null) {
            criteria = criteria.and("tipo").is(tipo);
        }
        if (estado != null) {
            criteria = criteria.and("estado").is(estado);
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("creadaEn"), Sort.Order.desc("_id")));
        Query query = new Query(criteria);
        long total = mongo.count(query, Notificacion.class);
        List<Notificacion> contenido = mongo.find(Query.of(query).with(pageable), Notificacion.class);
        return new PageImpl<>(contenido, pageable, total);
    }

    private static String normalizarClave(String clave) {
        return clave == null || clave.isBlank() ? null : clave.trim();
    }
}
