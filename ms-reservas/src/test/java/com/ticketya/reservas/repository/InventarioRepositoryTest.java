package com.ticketya.reservas.repository;

import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.model.InventarioSector;
import com.ticketya.reservas.model.Reserva;
import com.ticketya.reservas.model.enums.EstadoAsiento;
import com.ticketya.reservas.model.enums.EstadoReserva;
import com.ticketya.reservas.model.enums.TipoSector;
import com.ticketya.reservas.repository.projection.AvisoPendienteView;
import com.ticketya.reservas.repository.projection.DisponibilidadSectorView;
import com.ticketya.reservas.repository.projection.EstadoAvisoView;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Consultas nativas y restricciones de la base contra PostgreSQL real (Testcontainers).
 * Cada test corre en una transaccion que se revierte al final.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventarioRepositoryTest {

    private static final long EVENTO = 42L;
    private static final long NUMERADO = 11L;
    private static final long CAMPO = 12L;

    @Autowired
    private InventarioEventoRepository inventarioEventoRepository;
    @Autowired
    private InventarioSectorRepository inventarioSectorRepository;
    @Autowired
    private AsientoRepository asientoRepository;
    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    @Test
    void insertarSiNoExisteDevuelveCeroSiYaExisteYNoModificaNada() {
        assertThat(inventarioEventoRepository.insertarSiNoExiste(EVENTO, 4)).isEqualTo(1);
        assertThat(inventarioEventoRepository.insertarSiNoExiste(EVENTO, 9)).isZero();

        Integer max = jdbc.queryForObject("SELECT max_por_cliente FROM inventario_evento WHERE evento_id = ?",
                Integer.class, EVENTO);
        assertThat(max).isEqualTo(4);
        LocalDateTime creadoEn = jdbc.queryForObject("SELECT creado_en FROM inventario_evento WHERE evento_id = ?",
                LocalDateTime.class, EVENTO);
        assertThat(creadoEn).isNotNull();
    }

    @Test
    void insercionPorLotesCreaUnAsientoLibrePorFilaYNumeroConVersionCero() {
        InventarioSector sector = crearSector(NUMERADO, TipoSector.NUMERADO, 600);
        asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 20, 30);

        assertThat(asientoRepository.countByEventoIdAndSectorIdAndEstado(EVENTO, NUMERADO, EstadoAsiento.LIBRE))
                .isEqualTo(600);
        Integer distintos = jdbc.queryForObject(
                "SELECT count(DISTINCT (fila, numero)) FROM asiento WHERE evento_id = ? AND version = 0",
                Integer.class, EVENTO);
        assertThat(distintos).isEqualTo(600);
    }

    @Test
    void laBaseRechazaUnAsientoDuplicado() {
        InventarioSector sector = crearSector(NUMERADO, TipoSector.NUMERADO, 1);
        asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 1, 1);

        assertThrows(DataIntegrityViolationException.class,
                () -> asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 1, 1));
    }

    @Test
    void laBaseRechazaDisponiblesNegativo() {
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 5);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE inventario_sector SET disponibles = -1 WHERE evento_id = ? AND sector_id = ?", EVENTO, CAMPO));
    }

    @Test
    void reservarAsientoEsCondicionalAlEstadoLibreEIncrementaVersion() {
        InventarioSector sector = crearSector(NUMERADO, TipoSector.NUMERADO, 4);
        asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 2, 2);

        assertThat(asientoRepository.reservar(EVENTO, NUMERADO, 1, 2, UUID.randomUUID())).isEqualTo(1);
        assertThat(asientoRepository.reservar(EVENTO, NUMERADO, 1, 2, UUID.randomUUID())).isZero();
        assertThat(asientoRepository.reservar(EVENTO, NUMERADO, 9, 9, UUID.randomUUID())).isZero();

        Long version = jdbc.queryForObject(
                "SELECT version FROM asiento WHERE evento_id = ? AND sector_id = ? AND fila = 1 AND numero = 2",
                Long.class, EVENTO, NUMERADO);
        assertThat(version).isEqualTo(1L);
    }

    @Test
    void descontarCupoNoDejaDisponiblesNegativo() {
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 3);

        assertThat(inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 5)).isZero();
        assertThat(disponibles(CAMPO)).isEqualTo(3);
        assertThat(inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 3)).isEqualTo(1);
        assertThat(disponibles(CAMPO)).isZero();
        assertThat(inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 1)).isZero();
        assertThat(versionSector(CAMPO)).isEqualTo(1L);
    }

    @Test
    void sumarCupoNoSuperaLaCapacidad() {
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 3);
        inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 2);

        assertThat(inventarioSectorRepository.sumarCupo(EVENTO, CAMPO, 3)).isZero();
        assertThat(inventarioSectorRepository.sumarCupo(EVENTO, CAMPO, 2)).isEqualTo(1);
        assertThat(disponibles(CAMPO)).isEqualTo(3);
    }

    @Test
    void disponibilidadNoCuentaAsientosReservadosNiVendidos() {
        InventarioSector sector = crearSector(NUMERADO, TipoSector.NUMERADO, 4);
        asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 2, 2);
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 10);
        UUID vendida = UUID.randomUUID();
        asientoRepository.reservar(EVENTO, NUMERADO, 1, 1, UUID.randomUUID());
        asientoRepository.reservar(EVENTO, NUMERADO, 1, 2, vendida);
        asientoRepository.venderDeReserva(vendida);
        inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 3);

        List<DisponibilidadSectorView> filas = inventarioSectorRepository.disponibilidadPorEvento(EVENTO);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).getSectorId()).isEqualTo(NUMERADO);
        assertThat(filas.get(0).getTipo()).isEqualTo("NUMERADO");
        assertThat(filas.get(0).getCapacidad()).isEqualTo(4);
        assertThat(filas.get(0).getDisponibles()).isEqualTo(2L);
        assertThat(filas.get(1).getSectorId()).isEqualTo(CAMPO);
        assertThat(filas.get(1).getDisponibles()).isEqualTo(7L);
    }

    @Test
    void mapaDeAsientosOrdenadoPorFilaYNumeroYFiltrablePorEstado() {
        InventarioSector sector = crearSector(NUMERADO, TipoSector.NUMERADO, 6);
        asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 2, 3);
        asientoRepository.reservar(EVENTO, NUMERADO, 2, 1, UUID.randomUUID());

        var todos = asientoRepository.findByEventoIdAndSectorIdOrderByFilaAscNumeroAsc(EVENTO, NUMERADO);
        assertThat(todos).extracting(a -> a.getFila() + "-" + a.getNumero())
                .containsExactly("1-1", "1-2", "1-3", "2-1", "2-2", "2-3");

        var libres = asientoRepository.findByEventoIdAndSectorIdAndEstadoOrderByFilaAscNumeroAsc(
                EVENTO, NUMERADO, EstadoAsiento.LIBRE);
        assertThat(libres).hasSize(5).allMatch(a -> a.getEstado() == EstadoAsiento.LIBRE);
    }

    @Test
    void cierreLiberaSoloReservasPendientesYDevuelveSusLugares() {
        InventarioSector sector = crearSector(NUMERADO, TipoSector.NUMERADO, 4);
        asientoRepository.insertarAsientosLibres(sector.getId(), EVENTO, NUMERADO, 2, 2);
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 10);

        Reserva numerada = crearReserva(NUMERADO, 2, EstadoReserva.PENDIENTE);
        asientoRepository.reservar(EVENTO, NUMERADO, 1, 1, numerada.getId());
        asientoRepository.reservar(EVENTO, NUMERADO, 1, 2, numerada.getId());
        Reserva campo = crearReserva(CAMPO, 3, EstadoReserva.PENDIENTE);
        inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 3);
        Reserva confirmada = crearReserva(NUMERADO, 1, EstadoReserva.CONFIRMADA);
        asientoRepository.reservar(EVENTO, NUMERADO, 2, 1, confirmada.getId());
        asientoRepository.venderDeReserva(confirmada.getId());

        List<UUID> pendientes = reservaRepository.bloquearPendientesDeEvento(EVENTO);
        assertThat(pendientes).containsExactlyInAnyOrder(numerada.getId(), campo.getId());
        assertThat(reservaRepository.liberar(pendientes)).isEqualTo(2);
        assertThat(asientoRepository.liberarDeReservas(pendientes)).isEqualTo(2);
        assertThat(inventarioSectorRepository.devolverCupoDeReservas(pendientes)).isEqualTo(1);

        assertThat(asientoRepository.countByEventoIdAndSectorIdAndEstado(EVENTO, NUMERADO, EstadoAsiento.LIBRE))
                .isEqualTo(3);
        assertThat(asientoRepository.countByEventoIdAndSectorIdAndEstado(EVENTO, NUMERADO, EstadoAsiento.VENDIDO))
                .isEqualTo(1);
        assertThat(disponibles(CAMPO)).isEqualTo(10);
        assertThat(estadoReserva(confirmada.getId())).isEqualTo("CONFIRMADA");
        assertThat(reservaRepository.bloquearPendientesDeEvento(EVENTO)).isEmpty();
    }

    @Test
    void estadoAvisoYAvisosPendientesComparanLibresContraAgotadoNotificado() {
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 2);

        EstadoAvisoView estado = inventarioEventoRepository.estadoAviso(EVENTO).orElseThrow();
        assertThat(estado.getAbierto()).isTrue();
        assertThat(estado.getAgotadoNotificado()).isNull();
        assertThat(estado.getLibres()).isEqualTo(2L);
        assertThat(inventarioEventoRepository.avisosPendientes()).isEmpty();

        inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 2);
        List<AvisoPendienteView> pendientes = inventarioEventoRepository.avisosPendientes();
        assertThat(pendientes).hasSize(1);
        assertThat(pendientes.get(0).getEventoId()).isEqualTo(EVENTO);
        assertThat(pendientes.get(0).getAgotado()).isTrue();

        inventarioEventoRepository.marcarAgotadoNotificado(EVENTO, true);
        assertThat(inventarioEventoRepository.avisosPendientes()).isEmpty();

        inventarioEventoRepository.cerrar(EVENTO);
        inventarioSectorRepository.sumarCupo(EVENTO, CAMPO, 1);
        assertThat(inventarioEventoRepository.avisosPendientes()).as("cerrado no avisa").isEmpty();
    }

    @Test
    void abrirOlvidaElAvisoSoloSiEstabaCerrado() {
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 2);
        inventarioEventoRepository.marcarAgotadoNotificado(EVENTO, true);

        assertThat(inventarioEventoRepository.abrir(EVENTO)).isEqualTo(1);
        assertThat(agotadoNotificado()).isTrue();

        inventarioEventoRepository.cerrar(EVENTO);
        inventarioEventoRepository.abrir(EVENTO);
        assertThat(agotadoNotificado()).isNull();
        assertThat(inventarioEventoRepository.abrir(999L)).isZero();
        assertThat(inventarioEventoRepository.cerrar(999L)).isZero();
    }

    @Test
    void consultasDeReservaUsanElRelojDeLaBase() {
        crearSector(CAMPO, TipoSector.NO_NUMERADO, 10);
        LocalDateTime ahora = reservaRepository.ahora();
        assertThat(ahora).isNotNull();

        Reserva vigente = crearReserva(CAMPO, 2, EstadoReserva.PENDIENTE);
        Boolean mismaHora = jdbc.queryForObject(
                "SELECT creada_en = localtimestamp AND expira_en = localtimestamp + interval '10 minutes' "
                        + "FROM reserva WHERE id = ?", Boolean.class, vigente.getId());
        assertThat(mismaHora).as("creadaEn y expiraEn guardados sin corrimiento de zona").isTrue();
        Reserva vencida = crearReserva(CAMPO, 3, EstadoReserva.PENDIENTE);
        jdbc.update("UPDATE reserva SET expira_en = localtimestamp - interval '1 minute' WHERE id = ?",
                vencida.getId());
        crearReserva(CAMPO, 1, EstadoReserva.CONFIRMADA);

        assertThat(reservaRepository.bloquearClienteEvento(EVENTO, "101")).isEqualTo(1);
        assertThat(reservaRepository.unidadesContadas(EVENTO, "101")).isEqualTo(3L);
        assertThat(reservaRepository.bloquearVencidas(10)).containsExactly(vencida.getId());
        assertThat(reservaRepository.vencer(List.of(vencida.getId(), vigente.getId()))).isEqualTo(1);
        assertThat(reservaRepository.confirmar(vencida.getId())).isZero();
        assertThat(reservaRepository.confirmar(vigente.getId())).isEqualTo(1);
        assertThat(estadoReserva(vigente.getId())).isEqualTo("CONFIRMADA");
    }

    // ------------------------------------------------------------------ helpers

    private InventarioSector crearSector(long sectorId, TipoSector tipo, int capacidad) {
        inventarioEventoRepository.insertarSiNoExiste(EVENTO, 4);
        InventarioSector sector = new InventarioSector();
        sector.setInventarioEvento(inventarioEventoRepository.getReferenceById(EVENTO));
        sector.setSectorId(sectorId);
        sector.setTipo(tipo);
        sector.setCapacidadTotal(capacidad);
        sector.setDisponibles(capacidad);
        return inventarioSectorRepository.saveAndFlush(sector);
    }

    private Reserva crearReserva(long sectorId, int cantidad, EstadoReserva estado) {
        LocalDateTime ahora = reservaRepository.ahora();
        Reserva reserva = new Reserva();
        reserva.setEventoId(EVENTO);
        reserva.setSectorId(sectorId);
        reserva.setClienteId("101");
        reserva.setEstado(estado);
        reserva.setCantidad(cantidad);
        reserva.setCreadaEn(ahora);
        reserva.setExpiraEn(ahora.plusMinutes(10));
        Reserva guardada = reservaRepository.saveAndFlush(reserva);
        em.clear();
        return guardada;
    }

    private int disponibles(long sectorId) {
        return jdbc.queryForObject("SELECT disponibles FROM inventario_sector WHERE evento_id = ? AND sector_id = ?",
                Integer.class, EVENTO, sectorId);
    }

    private long versionSector(long sectorId) {
        return jdbc.queryForObject("SELECT version FROM inventario_sector WHERE evento_id = ? AND sector_id = ?",
                Long.class, EVENTO, sectorId);
    }

    private String estadoReserva(UUID id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    private Boolean agotadoNotificado() {
        return jdbc.queryForObject("SELECT agotado_notificado FROM inventario_evento WHERE evento_id = ?",
                Boolean.class, EVENTO);
    }
}
