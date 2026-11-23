package com.ticketya.recomendaciones.repository;

import com.ticketya.recomendaciones.model.Preferencias;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreferenciasRepository extends MongoRepository<Preferencias, String> {
}
