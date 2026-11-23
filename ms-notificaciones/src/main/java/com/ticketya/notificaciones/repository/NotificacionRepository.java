package com.ticketya.notificaciones.repository;

import com.ticketya.notificaciones.model.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificacionRepository extends MongoRepository<Notificacion, String> {

    Optional<Notificacion> findByClaveDeduplicacion(String claveDeduplicacion);
}
