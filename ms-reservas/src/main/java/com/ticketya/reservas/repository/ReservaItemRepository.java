package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.ReservaItem;
import com.ticketya.reservas.repository.projection.LugarView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Items de reserva. Un asiento devuelto conserva su item con cantidad 0: asi una devolucion repetida
 * se reconoce (200 sin cambios) y se distingue de un asiento que nunca fue de la reserva (409).
 */
public interface ReservaItemRepository extends JpaRepository<ReservaItem, Long> {

    /** Asientos vigentes (no devueltos) de una reserva, en orden de fila y numero. */
    @Query(value = "SELECT a.fila AS \"fila\", a.numero AS \"numero\" FROM reserva_item ri "
            + "JOIN asiento a ON a.id = ri.asiento_id "
            + "WHERE ri.reserva_id = :reservaId AND ri.cantidad > 0 ORDER BY a.fila, a.numero", nativeQuery = true)
    List<LugarView> lugaresVigentes(@Param("reservaId") UUID reservaId);

    /** Marca devuelto el item de un asiento. Devuelve 0 si no es de la reserva o ya estaba devuelto. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE reserva_item SET cantidad = 0 "
            + "WHERE reserva_id = :reservaId AND asiento_id = :asientoId AND cantidad > 0", nativeQuery = true)
    int marcarAsientoDevuelto(@Param("reservaId") UUID reservaId, @Param("asientoId") Long asientoId);

    @Query(value = "SELECT count(*) > 0 FROM reserva_item WHERE reserva_id = :reservaId AND asiento_id = :asientoId",
            nativeQuery = true)
    boolean asientoPerteneceAReserva(@Param("reservaId") UUID reservaId, @Param("asientoId") Long asientoId);

    /** Descuenta cupos devueltos del item de un sector no numerado, sin quedar negativo. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE reserva_item SET cantidad = cantidad - :cantidad "
            + "WHERE reserva_id = :reservaId AND asiento_id IS NULL AND cantidad >= :cantidad", nativeQuery = true)
    int descontarCupoDevuelto(@Param("reservaId") UUID reservaId, @Param("cantidad") int cantidad);
}
