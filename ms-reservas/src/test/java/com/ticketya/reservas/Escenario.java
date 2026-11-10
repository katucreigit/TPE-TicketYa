package com.ticketya.reservas;

import com.ticketya.reservas.dto.AsientoRef;
import com.ticketya.reservas.dto.InventarioRequest;
import com.ticketya.reservas.dto.ReservaRequest;
import com.ticketya.reservas.model.enums.TipoSector;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Arma escenarios contra la base real de los tests: inventarios y reservas por los servicios de produccion,
 * y lecturas directas con SQL para verificar el estado. Lo unico que se toca por SQL es expira_en, para simular
 * el paso del tiempo con el reloj de la base.
 */
public final class Escenario {

    private final JdbcTemplate jdbc;
    private final InventarioService inventarioService;
    private final ReservaService reservaService;

    public Escenario(JdbcTemplate jdbc, InventarioService inventarioService, ReservaService reservaService) {
        this.jdbc = jdbc;
        this.inventarioService = inventarioService;
        this.reservaService = reservaService;
    }

    public void limpiar() {
        jdbc.execute("TRUNCATE reserva_item, reserva, asiento, inventario_sector, inventario_evento "
                + "RESTART IDENTITY CASCADE");
    }

    public void inventario(long eventoId, int maxPorCliente, InventarioRequest.Sector... sectores) {
        inventarioService.crearInventario(new InventarioRequest(eventoId, maxPorCliente, List.of(sectores)));
    }

    public static InventarioRequest.Sector numerado(long sectorId, int filas, int asientosPorFila) {
        return new InventarioRequest.Sector(sectorId, TipoSector.NUMERADO, filas, asientosPorFila, null);
    }

    public static InventarioRequest.Sector noNumerado(long sectorId, int capacidad) {
        return new InventarioRequest.Sector(sectorId, TipoSector.NO_NUMERADO, null, null, capacidad);
    }

    /** Reserva por el servicio real los asientos indicados como pares fila, numero. */
    public UUID reservarAsientos(String clienteId, long eventoId, long sectorId, int... filaNumero) {
        return reservaService.crear(clienteId, new ReservaRequest(eventoId, sectorId, asientos(filaNumero), null))
                .reservaId();
    }

    public UUID reservarCantidad(String clienteId, long eventoId, long sectorId, int cantidad) {
        return reservaService.crear(clienteId, new ReservaRequest(eventoId, sectorId, null, cantidad)).reservaId();
    }

    public static List<AsientoRef> asientos(int... filaNumero) {
        if (filaNumero.length % 2 != 0) {
            throw new IllegalArgumentException("Se esperan pares fila, numero: " + Arrays.toString(filaNumero));
        }
        AsientoRef[] refs = new AsientoRef[filaNumero.length / 2];
        for (int i = 0; i < refs.length; i++) {
            refs[i] = new AsientoRef(filaNumero[2 * i], filaNumero[2 * i + 1]);
        }
        return List.of(refs);
    }

    /** Simula que pasaron los 10 minutos: expira_en queda en el pasado segun el reloj de la base. */
    public void expirar(UUID reservaId) {
        jdbc.update("UPDATE reserva SET expira_en = localtimestamp - interval '1 minute' WHERE id = ?", reservaId);
    }

    public String estado(UUID reservaId) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reservaId);
    }

    public int disponibles(long eventoId, long sectorId) {
        return entero("SELECT disponibles FROM inventario_sector WHERE evento_id = ? AND sector_id = ?",
                eventoId, sectorId);
    }

    public String estadoAsiento(long eventoId, long sectorId, int fila, int numero) {
        return jdbc.queryForObject("SELECT estado FROM asiento WHERE evento_id = ? AND sector_id = ? "
                + "AND fila = ? AND numero = ?", String.class, eventoId, sectorId, fila, numero);
    }

    public int entero(String sql, Object... args) {
        Integer valor = jdbc.queryForObject(sql, Integer.class, args);
        return valor == null ? 0 : valor;
    }
}
