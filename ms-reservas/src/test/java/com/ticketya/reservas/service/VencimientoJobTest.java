package com.ticketya.reservas.service;

import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ticketya.reservas.Escenario.noNumerado;
import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 3.7 Job de vencimiento invocado directamente (el scheduler esta desactivado en los tests).
 * Lote de 2 para comprobar que una corrida procesa todos los lotes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "ticketya.jobs.lote-vencimiento=2")
class VencimientoJobTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;
    private static final long CAMPO = 12L;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private VencimientoJob vencimientoJob;

    @MockitoBean
    private EventosClient eventosClient;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 4, numerado(PLATEA, 5, 10), noNumerado(CAMPO, 20));
    }

    @Test
    void venceTodasLasExpiradasEnVariosLotesYDevuelveAsientosYCupo() {
        List<UUID> expiradas = List.of(
                e.reservarAsientos("101", EVENTO, PLATEA, 1, 1, 1, 2),
                e.reservarAsientos("102", EVENTO, PLATEA, 2, 5),
                e.reservarCantidad("103", EVENTO, CAMPO, 3),
                e.reservarCantidad("104", EVENTO, CAMPO, 4),
                e.reservarCantidad("105", EVENTO, CAMPO, 1));
        expiradas.forEach(e::expirar);
        assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(12);

        vencimientoJob.ejecutar();

        assertThat(expiradas).allSatisfy(id -> assertThat(e.estado(id)).isEqualTo("VENCIDA"));
        assertThat(e.entero("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE' OR reserva_id IS NOT NULL")).isZero();
        assertThat(e.disponibles(EVENTO, CAMPO)).as("el cupo de los no numerados vuelve completo").isEqualTo(20);
    }

    @Test
    void noModificaConfirmadasLiberadasNiPendientesVigentes() {
        UUID confirmada = e.reservarAsientos("101", EVENTO, PLATEA, 3, 3);
        reservaService.confirmar(confirmada);
        UUID confirmadaCupo = e.reservarCantidad("102", EVENTO, CAMPO, 2);
        reservaService.confirmar(confirmadaCupo);
        UUID liberada = e.reservarCantidad("103", EVENTO, CAMPO, 2);
        reservaService.liberar(liberada);
        UUID vigente = e.reservarCantidad("104", EVENTO, CAMPO, 3);
        // Aunque su plazo ya paso, una reserva confirmada o liberada no vence.
        e.expirar(confirmada);
        e.expirar(confirmadaCupo);
        e.expirar(liberada);
        int versionCampo = versionCampo();

        vencimientoJob.ejecutar();

        assertThat(e.estado(confirmada)).isEqualTo("CONFIRMADA");
        assertThat(e.estado(confirmadaCupo)).isEqualTo("CONFIRMADA");
        assertThat(e.estado(liberada)).isEqualTo("LIBERADA");
        assertThat(e.estado(vigente)).isEqualTo("PENDIENTE");
        assertThat(e.estadoAsiento(EVENTO, PLATEA, 3, 3)).isEqualTo("VENDIDO");
        assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(15);
        assertThat(versionCampo()).isEqualTo(versionCampo);
    }

    @Test
    void correrElJobDosVecesNoDevuelveLosLugaresDosVeces() {
        UUID asientos = e.reservarAsientos("101", EVENTO, PLATEA, 4, 4);
        UUID cupo = e.reservarCantidad("102", EVENTO, CAMPO, 4);
        e.expirar(asientos);
        e.expirar(cupo);

        vencimientoJob.ejecutar();
        int versionCampo = versionCampo();
        int versionAsiento = e.entero("SELECT version FROM asiento WHERE fila = 4 AND numero = 4");
        vencimientoJob.ejecutar();

        assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(20);
        assertThat(versionCampo()).isEqualTo(versionCampo);
        assertThat(e.entero("SELECT version FROM asiento WHERE fila = 4 AND numero = 4")).isEqualTo(versionAsiento);
    }

    @Test
    void losLugaresVencidosQuedanDisponiblesParaOtroCliente() {
        UUID asientos = e.reservarAsientos("101", EVENTO, PLATEA, 5, 5);
        UUID cupo = e.reservarCantidad("102", EVENTO, CAMPO, 4);
        e.expirar(asientos);
        e.expirar(cupo);

        vencimientoJob.ejecutar();

        e.reservarAsientos("202", EVENTO, PLATEA, 5, 5);
        assertThat(e.estadoAsiento(EVENTO, PLATEA, 5, 5)).isEqualTo("RESERVADO");
        assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(20);
    }

    @Test
    void unaReservaBloqueadaPorOtraTransaccionSeSalteaYSeVenceEnLaCorridaSiguiente() throws Exception {
        UUID bloqueada = e.reservarCantidad("101", EVENTO, CAMPO, 3);
        UUID libre = e.reservarCantidad("102", EVENTO, CAMPO, 2);
        e.expirar(bloqueada);
        e.expirar(libre);
        CountDownLatch tomada = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            // Simula otra instancia del job que esta procesando esa reserva.
            Future<?> otraInstancia = hilo.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM reserva WHERE id = ? FOR UPDATE", UUID.class, bloqueada);
                tomada.countDown();
                try {
                    soltar.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(tomada.await(5, TimeUnit.SECONDS)).isTrue();

            vencimientoJob.ejecutar();

            assertThat(e.estado(libre)).isEqualTo("VENCIDA");
            assertThat(e.estado(bloqueada)).isEqualTo("PENDIENTE");
            assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(17);

            soltar.countDown();
            otraInstancia.get(10, TimeUnit.SECONDS);
        } finally {
            hilo.shutdownNow();
        }

        vencimientoJob.ejecutar();

        assertThat(e.estado(bloqueada)).isEqualTo("VENCIDA");
        assertThat(e.disponibles(EVENTO, CAMPO)).isEqualTo(20);
    }

    @Test
    void siElVencimientoDevuelveLugaresAUnEventoAgotadoAvisaAMsEventos() {
        e.limpiar();
        e.inventario(EVENTO, 4, noNumerado(CAMPO, 2));
        UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 2);
        verify(eventosClient).actualizarDisponibilidad(EVENTO, true);
        e.expirar(id);

        vencimientoJob.ejecutar();

        verify(eventosClient).actualizarDisponibilidad(EVENTO, false);
        assertThat(jdbc.queryForObject("SELECT agotado_notificado FROM inventario_evento WHERE evento_id = ?",
                Boolean.class, EVENTO)).isFalse();
    }

    private int versionCampo() {
        return e.entero("SELECT version FROM inventario_sector WHERE evento_id = ? AND sector_id = ?", EVENTO, CAMPO);
    }
}
