package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.InventarioEvento;
import com.ticketya.reservas.repository.projection.AvisoPendienteView;
import com.ticketya.reservas.repository.projection.EstadoAvisoView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventarioEventoRepository extends JpaRepository<InventarioEvento, Long> {

    /**
     * Crea el inventario abierto si no existe. La fecha sale de la base (localtimestamp).
     * Devuelve 0 si ya existia: la unicidad la resuelve la PK, sin depender de una excepcion.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO inventario_evento (evento_id, abierto, max_por_cliente, creado_en, agotado_notificado) "
            + "VALUES (:eventoId, true, :maxPorCliente, localtimestamp, NULL) "
            + "ON CONFLICT (evento_id) DO NOTHING", nativeQuery = true)
    int insertarSiNoExiste(@Param("eventoId") Long eventoId, @Param("maxPorCliente") int maxPorCliente);

    /** Marca el inventario como cerrado. Devuelve 0 si el evento no tiene inventario. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventario_evento SET abierto = false WHERE evento_id = :eventoId", nativeQuery = true)
    int cerrar(@Param("eventoId") Long eventoId);

    /**
     * Reabre el inventario. Si estaba cerrado, olvida el ultimo aviso: al reanudar, ms-eventos deja el evento
     * en PUBLICADO, asi que todavia no se notifico nada para este nuevo periodo abierto.
     * Devuelve 0 si el evento no tiene inventario.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventario_evento SET "
            + "agotado_notificado = CASE WHEN abierto THEN agotado_notificado ELSE NULL END, "
            + "abierto = true WHERE evento_id = :eventoId", nativeQuery = true)
    int abrir(@Param("eventoId") Long eventoId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventario_evento SET agotado_notificado = :agotado WHERE evento_id = :eventoId",
            nativeQuery = true)
    int marcarAgotadoNotificado(@Param("eventoId") Long eventoId, @Param("agotado") boolean agotado);

    @Query(value = "SELECT ie.abierto AS \"abierto\", ie.agotado_notificado AS \"agotadoNotificado\", "
            + InventarioSql.LIBRES_EVENTO + " AS \"libres\" "
            + "FROM inventario_evento ie WHERE ie.evento_id = :eventoId", nativeQuery = true)
    Optional<EstadoAvisoView> estadoAviso(@Param("eventoId") Long eventoId);

    /**
     * Eventos abiertos cuyo estado real de agotado difiere del ultimo aviso confirmado.
     * Null se toma como no agotado: es el estado en que ms-eventos deja el evento al publicarlo o reanudarlo.
     */
    @Query(value = "SELECT x.evento_id AS \"eventoId\", x.agotado AS \"agotado\" FROM ("
            + "SELECT ie.evento_id, ie.agotado_notificado, (" + InventarioSql.LIBRES_EVENTO + " = 0) AS agotado "
            + "FROM inventario_evento ie WHERE ie.abierto) x "
            + "WHERE x.agotado <> COALESCE(x.agotado_notificado, false) "
            + "ORDER BY x.evento_id", nativeQuery = true)
    List<AvisoPendienteView> avisosPendientes();
}
