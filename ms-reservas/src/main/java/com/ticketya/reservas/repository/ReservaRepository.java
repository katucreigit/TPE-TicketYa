package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    /** Hora actual de la base (timestamp sin zona). Nunca se usa el reloj de la JVM. */
    @Query(value = "SELECT localtimestamp", nativeQuery = true)
    LocalDateTime ahora();

    /**
     * Serializa las reservas del mismo cliente en el mismo evento hasta el fin de la transaccion.
     * pg_advisory_xact_lock devuelve void, por eso se envuelve en un SELECT que devuelve 1.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(CAST(:eventoId AS text) || ':' || CAST(:clienteId AS text)))) l",
            nativeQuery = true)
    int bloquearClienteEvento(@Param("eventoId") Long eventoId, @Param("clienteId") String clienteId);

    /** Unidades que cuentan para el limite: PENDIENTE no vencidas y CONFIRMADA. */
    @Query(value = "SELECT COALESCE(SUM(cantidad), 0) FROM reserva "
            + "WHERE evento_id = :eventoId AND cliente_id = :clienteId "
            + "AND ((estado = 'PENDIENTE' AND expira_en > localtimestamp) OR estado = 'CONFIRMADA')",
            nativeQuery = true)
    long unidadesContadas(@Param("eventoId") Long eventoId, @Param("clienteId") String clienteId);

    /** Confirmar: solo si sigue PENDIENTE y vigente. Excluyente con {@link #vencer}. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE reserva SET estado = 'CONFIRMADA' "
            + "WHERE id = :id AND estado = 'PENDIENTE' AND expira_en > localtimestamp", nativeQuery = true)
    int confirmar(@Param("id") UUID id);

    /** Bloquea reservas PENDIENTE vencidas; SKIP LOCKED evita que dos instancias procesen la misma. */
    @Query(value = "SELECT id FROM reserva WHERE estado = 'PENDIENTE' AND expira_en <= localtimestamp "
            + "ORDER BY expira_en LIMIT :limite FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<UUID> bloquearVencidas(@Param("limite") int limite);

    /** Vencer: solo si sigue PENDIENTE y ya expiro. Excluyente con {@link #confirmar}. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE reserva SET estado = 'VENCIDA' "
            + "WHERE id IN (:ids) AND estado = 'PENDIENTE' AND expira_en <= localtimestamp", nativeQuery = true)
    int vencer(@Param("ids") Collection<UUID> ids);

    /** Bloquea las reservas PENDIENTE de un evento (cierre del inventario). */
    @Query(value = "SELECT id FROM reserva WHERE evento_id = :eventoId AND estado = 'PENDIENTE' "
            + "ORDER BY id FOR UPDATE", nativeQuery = true)
    List<UUID> bloquearPendientesDeEvento(@Param("eventoId") Long eventoId);

    /** Estado confirmado en la base (sin pasar por el contexto de persistencia). */
    @Query(value = "SELECT estado FROM reserva WHERE id = :id", nativeQuery = true)
    Optional<String> estadoActual(@Param("id") UUID id);

    /** Bloquea la reserva y devuelve su estado; serializa las devoluciones sobre la misma reserva. */
    @Query(value = "SELECT estado FROM reserva WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<String> bloquearEstado(@Param("id") UUID id);

    /** Devolucion: descuenta unidades de una reserva CONFIRMADA sin quedar negativa. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE reserva SET cantidad = cantidad - :cantidad "
            + "WHERE id = :id AND estado = 'CONFIRMADA' AND cantidad >= :cantidad", nativeQuery = true)
    int descontarCantidad(@Param("id") UUID id, @Param("cantidad") int cantidad);

    /** Pasa a LIBERADA las reservas indicadas que siguen PENDIENTE. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE reserva SET estado = 'LIBERADA' WHERE id IN (:ids) AND estado = 'PENDIENTE'",
            nativeQuery = true)
    int liberar(@Param("ids") Collection<UUID> ids);
}
