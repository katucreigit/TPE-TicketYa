package com.ticketya.reservas.repository;

/** Insercion por lotes de los asientos pregenerados. */
public interface AsientoBatchRepository {

    /**
     * Inserta un asiento LIBRE (version 0) por cada fila y numero del sector, con batch JDBC.
     * Participa en la transaccion en curso: si algo falla despues, no queda ningun asiento creado.
     */
    void insertarAsientosLibres(Long inventarioSectorId, Long eventoId, Long sectorId, int filas, int asientosPorFila);
}
