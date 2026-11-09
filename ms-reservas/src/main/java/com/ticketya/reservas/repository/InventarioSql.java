package com.ticketya.reservas.repository;

/**
 * Fragmentos SQL compartidos por las consultas nativas de inventario.
 * Los lugares libres siempre se calculan en la base: en sectores numerados se cuentan los asientos LIBRE
 * y en no numerados se usa el cupo atomico {@code disponibles}.
 */
final class InventarioSql {

    /** Lugares libres de un sector; requiere el alias {@code s} para inventario_sector. */
    static final String LIBRES_SECTOR =
            "CASE WHEN s.tipo = 'NUMERADO' THEN "
                    + "(SELECT count(*) FROM asiento a WHERE a.evento_id = s.evento_id "
                    + "AND a.sector_id = s.sector_id AND a.estado = 'LIBRE') "
                    + "ELSE s.disponibles END";

    /** Suma de lugares libres de un evento; requiere el alias {@code ie} para inventario_evento. */
    static final String LIBRES_EVENTO =
            "(SELECT COALESCE(SUM(" + LIBRES_SECTOR + "), 0) FROM inventario_sector s "
                    + "WHERE s.evento_id = ie.evento_id)";

    private InventarioSql() {
    }
}
