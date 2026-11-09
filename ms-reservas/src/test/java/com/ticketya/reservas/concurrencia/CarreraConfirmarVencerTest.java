package com.ticketya.reservas.concurrencia;

import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.exception.ApiException;
import com.ticketya.reservas.repository.ReservaRepository;
import com.ticketya.reservas.service.InventarioService;
import com.ticketya.reservas.service.ReservaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 3.5 Carrera entre confirmar y vencer sobre la misma reserva: solo una de las dos operaciones se aplica.
 * <p>
 * Las dos intercalaciones se fuerzan con transacciones reales que retienen el lock de la fila. La confirmacion
 * empieza antes del vencimiento (su localtimestamp es anterior a expira_en) y el job corre despues (su
 * localtimestamp es posterior), de modo que, mirando solo las fechas, ambas operaciones serian aplicables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CarreraConfirmarVencerTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private ReservaRepository reservaRepository;

    @MockitoBean
    private EventosClient eventosClient;

    private Escenario e;
    private ExecutorService hilos;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 4, numerado(PLATEA, 1, 30));
        hilos = Executors.newCachedThreadPool();
    }

    @AfterEach
    void cerrarHilos() {
        hilos.shutdownNow();
    }

    @Test
    void elJobBloqueaPrimeroLaConfirmacionEsperaElLockYRecibe410() throws Exception {
        UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 1, 1);
        venceEnUnSegundo(id);
        CountDownLatch confirmacionIniciada = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);
        CountDownLatch jobBloqueo = new CountDownLatch(1);
        CountDownLatch jobCommit = new CountDownLatch(1);

        // Transaccion de la confirmacion: arranca antes del vencimiento y fija su localtimestamp.
        Future<?> confirmacion = hilos.submit(() -> tx.executeWithoutResult(s -> {
            reservaRepository.ahora();
            confirmacionIniciada.countDown();
            esperar(confirmar);
            reservaService.confirmar(id);
        }));
        assertThat(confirmacionIniciada.await(5, TimeUnit.SECONDS)).isTrue();
        esperarQueLaBaseLaDeComoVencida(id);

        // El job toma la fila con FOR UPDATE SKIP LOCKED y la vence, pero todavia no confirma su transaccion.
        Future<Boolean> job = hilos.submit(() -> tx.execute(s -> {
            reservaService.vencerLote();
            jobBloqueo.countDown();
            esperar(jobCommit);
            return true;
        }));
        assertThat(jobBloqueo.await(5, TimeUnit.SECONDS)).isTrue();

        confirmar.countDown();
        Thread.sleep(500);
        assertThat(confirmacion.isDone()).as("la confirmacion espera el lock de la fila que tiene el job").isFalse();

        jobCommit.countDown();
        job.get(10, TimeUnit.SECONDS);
        ExecutionException falla = catchThrowableOfType(ExecutionException.class,
                () -> confirmacion.get(10, TimeUnit.SECONDS));
        assertThat(falla.getCause()).isInstanceOf(ApiException.class);
        assertThat(((ApiException) falla.getCause()).getCode()).isEqualTo("RESERVA_VENCIDA");

        assertThat(e.estado(id)).isEqualTo("VENCIDA");
        assertThat(e.estadoAsiento(EVENTO, PLATEA, 1, 1)).isEqualTo("LIBRE");
        assertThat(e.entero("SELECT count(*) FROM asiento WHERE estado = 'VENDIDO' OR reserva_id IS NOT NULL")).isZero();
    }

    @Test
    void laConfirmacionBloqueaPrimeroYElJobLaSalteaYNoLaVence() throws Exception {
        UUID id = e.reservarAsientos("101", EVENTO, PLATEA, 1, 1);
        venceEnUnSegundo(id);
        CountDownLatch confirmo = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);

        // Confirma dentro del plazo pero retiene el commit hasta despues del vencimiento.
        Future<?> confirmacion = hilos.submit(() -> tx.executeWithoutResult(s -> {
            reservaRepository.ahora();
            reservaService.confirmar(id);
            confirmo.countDown();
            esperar(commit);
        }));
        assertThat(confirmo.await(5, TimeUnit.SECONDS)).isTrue();
        esperarQueLaBaseLaDeComoVencida(id);

        // Para el job la reserva es PENDIENTE y vencida, pero la fila esta bloqueada: SKIP LOCKED la saltea.
        assertThat(reservaService.vencerLote()).isFalse();
        assertThat(e.estado(id)).isEqualTo("PENDIENTE");

        commit.countDown();
        confirmacion.get(10, TimeUnit.SECONDS);
        reservaService.vencerLote();

        assertThat(e.estado(id)).isEqualTo("CONFIRMADA");
        assertThat(e.estadoAsiento(EVENTO, PLATEA, 1, 1)).isEqualTo("VENDIDO");
    }

    /**
     * Carrera libre: 25 reservas vencen casi a la vez mientras el job corre sin parar y cada reserva se confirma
     * en un momento distinto alrededor del vencimiento. Cada reserva termina en uno solo de los dos caminos y su
     * resultado coincide con lo que recibio ms-ventas.
     */
    @Test
    void conMuchasReservasCadaUnaTerminaConfirmadaOVencidaNuncaAmbas() throws Exception {
        int n = 25;
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ids.add(e.reservarAsientos("cliente-" + i, EVENTO, PLATEA, 1, i));
        }
        jdbc.update("UPDATE reserva SET expira_en = localtimestamp + interval '600 milliseconds'");

        AtomicBoolean corriendo = new AtomicBoolean(true);
        Future<?> job = hilos.submit(() -> {
            while (corriendo.get()) {
                reservaService.vencerLote();
                Thread.sleep(10);
            }
            return null;
        });
        List<Future<Boolean>> confirmaciones = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID id = ids.get(i);
            long demora = 50L * i;
            confirmaciones.add(hilos.submit(() -> {
                Thread.sleep(demora);
                try {
                    reservaService.confirmar(id);
                    return true;
                } catch (ApiException ex) {
                    assertThat(ex.getCode()).isEqualTo("RESERVA_VENCIDA");
                    return false;
                }
            }));
        }
        List<Boolean> confirmadas = new ArrayList<>();
        for (Future<Boolean> f : confirmaciones) {
            confirmadas.add(f.get(30, TimeUnit.SECONDS));
        }
        corriendo.set(false);
        job.get(10, TimeUnit.SECONDS);
        reservaService.vencerLote();

        for (int i = 0; i < n; i++) {
            String estadoAsiento = e.estadoAsiento(EVENTO, PLATEA, 1, i + 1);
            if (confirmadas.get(i)) {
                assertThat(e.estado(ids.get(i))).isEqualTo("CONFIRMADA");
                assertThat(estadoAsiento).isEqualTo("VENDIDO");
            } else {
                assertThat(e.estado(ids.get(i))).isEqualTo("VENCIDA");
                assertThat(estadoAsiento).isEqualTo("LIBRE");
            }
        }
        assertThat(confirmadas).as("la carrera ejercito ambos caminos").contains(true, false);
    }

    // ------------------------------------------------------------------ helpers

    private void venceEnUnSegundo(UUID id) {
        jdbc.update("UPDATE reserva SET expira_en = localtimestamp + interval '1 second' WHERE id = ?", id);
    }

    /** Espera a que el reloj de la base pase expira_en (cada consulta es su propia transaccion). */
    private void esperarQueLaBaseLaDeComoVencida(UUID id) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT localtimestamp > expira_en FROM reserva WHERE id = ?", Boolean.class, id))) {
            assertThat(System.nanoTime()).as("la reserva no vencio a tiempo").isLessThan(limite);
            Thread.sleep(50);
        }
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("El test no libero el latch a tiempo");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
