package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReintegroRepository extends JpaRepository<Reintegro, UUID> {

    /** A lo sumo uno por UNIQUE(entrada_id, tipo): un reintento reutiliza esta fila. */
    Optional<Reintegro> findByEntradaIdAndTipo(UUID entradaId, TipoReintegro tipo);
}
