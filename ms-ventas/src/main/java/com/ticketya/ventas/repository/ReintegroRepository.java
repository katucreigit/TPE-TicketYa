package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReintegroRepository extends JpaRepository<Reintegro, UUID> {

    /** A lo sumo uno por UNIQUE(entrada_id, tipo): un reintento reutiliza esta fila. */
    Optional<Reintegro> findByEntradaIdAndTipo(UUID entradaId, TipoReintegro tipo);

    /** Reintegros de un tipo y estado de las entradas de un evento, con la entrada, la compra y el pago. */
    @Query("""
            SELECT r FROM Reintegro r
            JOIN FETCH r.entrada e
            JOIN FETCH e.compra c
            LEFT JOIN FETCH c.pago
            WHERE e.eventoId = :eventoId AND r.tipo = :tipo AND r.estado = :estado
            ORDER BY r.id
            """)
    List<Reintegro> buscarPorEvento(@Param("eventoId") Long eventoId,
                                    @Param("tipo") TipoReintegro tipo,
                                    @Param("estado") EstadoReintegro estado);

    long countByEntradaEventoIdAndTipoAndEstado(Long eventoId, TipoReintegro tipo, EstadoReintegro estado);

    /** SELECT ... FOR UPDATE: dos cancelaciones simultaneas no completan dos veces el mismo reintegro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reintegro r JOIN FETCH r.entrada WHERE r.id = :id")
    Optional<Reintegro> bloquear(@Param("id") UUID id);
}
