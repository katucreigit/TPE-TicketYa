package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.InventarioSector;
import com.ticketya.reservas.repository.projection.DisponibilidadSectorView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventarioSectorRepository extends JpaRepository<InventarioSector, Long> {

    Optional<InventarioSector> findByInventarioEventoEventoIdAndSectorId(Long eventoId, Long sectorId);

    /** Lugares libres por sector, calculados en la base en cada consulta (sin cache). */
    @Query(value = "SELECT s.sector_id AS \"sectorId\", s.tipo AS \"tipo\", s.capacidad_total AS \"capacidad\", "
            + InventarioSql.LIBRES_SECTOR + " AS \"disponibles\" "
            + "FROM inventario_sector s WHERE s.evento_id = :eventoId ORDER BY s.sector_id", nativeQuery = true)
    List<DisponibilidadSectorView> disponibilidadPorEvento(@Param("eventoId") Long eventoId);

    /**
     * Resta atomica del cupo de un sector no numerado. Devuelve 0 si no alcanza (CUPO_INSUFICIENTE);
     * el CHECK (disponibles >= 0) es la segunda barrera.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventario_sector SET disponibles = disponibles - :cantidad, version = version + 1 "
            + "WHERE evento_id = :eventoId AND sector_id = :sectorId AND tipo = 'NO_NUMERADO' "
            + "AND disponibles >= :cantidad", nativeQuery = true)
    int descontarCupo(@Param("eventoId") Long eventoId, @Param("sectorId") Long sectorId,
                      @Param("cantidad") int cantidad);

    /** Devuelve cupo a un sector no numerado sin superar la capacidad. Devuelve 0 si no aplica. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventario_sector SET disponibles = disponibles + :cantidad, version = version + 1 "
            + "WHERE evento_id = :eventoId AND sector_id = :sectorId AND tipo = 'NO_NUMERADO' "
            + "AND disponibles + :cantidad <= capacidad_total", nativeQuery = true)
    int sumarCupo(@Param("eventoId") Long eventoId, @Param("sectorId") Long sectorId,
                  @Param("cantidad") int cantidad);

    /**
     * Devuelve al cupo la cantidad de las reservas indicadas que son de sectores no numerados.
     * Se usa al liberar o vencer reservas PENDIENTE, ya bloqueadas y cambiadas de estado en la misma transaccion.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventario_sector s SET disponibles = s.disponibles + x.total, version = s.version + 1 "
            + "FROM (SELECT r.evento_id, r.sector_id, SUM(r.cantidad) AS total FROM reserva r "
            + "WHERE r.id IN (:reservaIds) GROUP BY r.evento_id, r.sector_id) x "
            + "WHERE s.evento_id = x.evento_id AND s.sector_id = x.sector_id AND s.tipo = 'NO_NUMERADO'",
            nativeQuery = true)
    int devolverCupoDeReservas(@Param("reservaIds") Collection<UUID> reservaIds);
}
