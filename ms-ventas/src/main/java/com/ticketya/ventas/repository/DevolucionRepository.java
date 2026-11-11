package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Devolucion;
import com.ticketya.ventas.model.enums.EstadoDevolucion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DevolucionRepository extends JpaRepository<Devolucion, UUID> {

    /** Detalle con la entrada y su compra (para validar el dueno y armar la respuesta). */
    @EntityGraph(attributePaths = {"entrada", "entrada.compra"})
    Optional<Devolucion> findDetalleById(UUID id);

    /** SELECT ... FOR UPDATE: serializa aprobaciones y rechazos simultaneos de la misma devolucion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM Devolucion d
            JOIN FETCH d.entrada e
            JOIN FETCH e.compra
            WHERE d.id = :id
            """)
    Optional<Devolucion> bloquear(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"entrada"})
    @Query("""
            SELECT d FROM Devolucion d
            WHERE (:estado IS NULL OR d.estado = :estado)
            """)
    Page<Devolucion> buscar(@Param("estado") EstadoDevolucion estado, Pageable pageable);

    boolean existsByEntradaIdAndEstadoIn(UUID entradaId, Collection<EstadoDevolucion> estados);
}
