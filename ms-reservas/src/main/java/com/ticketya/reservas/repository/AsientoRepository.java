package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.Asiento;
import com.ticketya.reservas.model.enums.EstadoAsiento;
import com.ticketya.reservas.repository.projection.AsientoView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsientoRepository extends JpaRepository<Asiento, Long>, AsientoBatchRepository {

    List<AsientoView> findByEventoIdAndSectorIdOrderByFilaAscNumeroAsc(Long eventoId, Long sectorId);

    List<AsientoView> findByEventoIdAndSectorIdAndEstadoOrderByFilaAscNumeroAsc(Long eventoId, Long sectorId,
                                                                                EstadoAsiento estado);

    long countByEventoIdAndSectorIdAndEstado(Long eventoId, Long sectorId, EstadoAsiento estado);

    /**
     * UPDATE condicional del design: reserva el asiento solo si esta LIBRE.
     * Devuelve 0 si el asiento no existe o no estaba libre.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE asiento SET estado = 'RESERVADO', reserva_id = :reservaId, version = version + 1 "
            + "WHERE evento_id = :eventoId AND sector_id = :sectorId AND fila = :fila AND numero = :numero "
            + "AND estado = 'LIBRE'", nativeQuery = true)
    int reservar(@Param("eventoId") Long eventoId, @Param("sectorId") Long sectorId,
                 @Param("fila") int fila, @Param("numero") int numero, @Param("reservaId") UUID reservaId);

    boolean existsByEventoIdAndSectorIdAndFilaAndNumero(Long eventoId, Long sectorId, int fila, int numero);

    @Query(value = "SELECT id FROM asiento WHERE evento_id = :eventoId AND sector_id = :sectorId "
            + "AND fila = :fila AND numero = :numero", nativeQuery = true)
    Optional<Long> idDeLugar(@Param("eventoId") Long eventoId, @Param("sectorId") Long sectorId,
                             @Param("fila") int fila, @Param("numero") int numero);

    /** Ids de los asientos tomados por una reserva, en orden de fila y numero. */
    @Query(value = "SELECT id FROM asiento WHERE reserva_id = :reservaId ORDER BY fila, numero", nativeQuery = true)
    List<Long> idsDeReserva(@Param("reservaId") UUID reservaId);

    /** Devolucion: el asiento VENDIDO de la reserva vuelve a LIBRE. Devuelve 0 si no estaba en ese estado. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE asiento SET estado = 'LIBRE', reserva_id = NULL, version = version + 1 "
            + "WHERE id = :asientoId AND reserva_id = :reservaId AND estado = 'VENDIDO'", nativeQuery = true)
    int liberarVendido(@Param("asientoId") Long asientoId, @Param("reservaId") UUID reservaId);

    /** Pasa a VENDIDO los asientos RESERVADO de una reserva (confirmacion). */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE asiento SET estado = 'VENDIDO', version = version + 1 "
            + "WHERE reserva_id = :reservaId AND estado = 'RESERVADO'", nativeQuery = true)
    int venderDeReserva(@Param("reservaId") UUID reservaId);

    /** Devuelve a LIBRE los asientos RESERVADO de las reservas indicadas (liberacion, vencimiento o cierre). */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE asiento SET estado = 'LIBRE', reserva_id = NULL, version = version + 1 "
            + "WHERE reserva_id IN (:reservaIds) AND estado = 'RESERVADO'", nativeQuery = true)
    int liberarDeReservas(@Param("reservaIds") Collection<UUID> reservaIds);
}
