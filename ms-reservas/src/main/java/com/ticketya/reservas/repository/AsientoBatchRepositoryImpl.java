package com.ticketya.reservas.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementacion con {@link JdbcTemplate#batchUpdate}. El JpaTransactionManager expone su conexion JDBC,
 * asi que el batch corre dentro de la misma transaccion que el resto de la inicializacion.
 */
class AsientoBatchRepositoryImpl implements AsientoBatchRepository {

    static final int TAMANIO_LOTE = 500;

    private static final String INSERT =
            "INSERT INTO asiento (inventario_sector_id, evento_id, sector_id, fila, numero, estado, reserva_id, version) "
                    + "VALUES (?, ?, ?, ?, ?, 'LIBRE', NULL, 0)";

    private final JdbcTemplate jdbcTemplate;

    AsientoBatchRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertarAsientosLibres(Long inventarioSectorId, Long eventoId, Long sectorId,
                                       int filas, int asientosPorFila) {
        List<int[]> lugares = new ArrayList<>(filas * asientosPorFila);
        for (int fila = 1; fila <= filas; fila++) {
            for (int numero = 1; numero <= asientosPorFila; numero++) {
                lugares.add(new int[]{fila, numero});
            }
        }
        jdbcTemplate.batchUpdate(INSERT, lugares, TAMANIO_LOTE, (ps, lugar) -> {
            ps.setLong(1, inventarioSectorId);
            ps.setLong(2, eventoId);
            ps.setLong(3, sectorId);
            ps.setInt(4, lugar[0]);
            ps.setInt(5, lugar[1]);
        });
    }
}
