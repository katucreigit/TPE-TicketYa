package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {

    @EntityGraph(attributePaths = {"compra"})
    Optional<Entrada> findWithCompraById(UUID id);

    /**
     * SELECT ... FOR UPDATE de la entrada con su compra: dos solicitudes de devolucion simultaneas sobre la misma
     * entrada se serializan y la segunda ve el estado DEVOLUCION_SOLICITADA que dejo la primera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Entrada e JOIN FETCH e.compra WHERE e.id = :id")
    Optional<Entrada> bloquearConCompra(@Param("id") UUID id);

    @Query("""
            SELECT e FROM Entrada e
            WHERE e.compra.clienteId = :clienteId
              AND (:estado IS NULL OR e.estado = :estado)
              AND (:eventoId IS NULL OR e.eventoId = :eventoId)
            """)
    Page<Entrada> buscarDelCliente(@Param("clienteId") String clienteId,
                                   @Param("estado") EstadoEntrada estado,
                                   @Param("eventoId") Long eventoId,
                                   Pageable pageable);
}
