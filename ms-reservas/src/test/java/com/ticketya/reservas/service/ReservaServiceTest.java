package com.ticketya.reservas.service;

import com.ticketya.reservas.Escenario;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.dto.DevolucionRequest;
import com.ticketya.reservas.dto.ReservaRequest;
import com.ticketya.reservas.exception.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static com.ticketya.reservas.Escenario.asientos;
import static com.ticketya.reservas.Escenario.noNumerado;
import static com.ticketya.reservas.Escenario.numerado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 3.1 (computo del limite y codigos de error de cada situacion) y 3.6 (todo o nada) sobre PostgreSQL real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReservaServiceTest {

    private static final long EVENTO = 42L;
    private static final long PLATEA = 11L;
    private static final long CAMPO = 12L;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private ReservaService reservaService;

    @MockitoBean
    private EventosClient eventosClient;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario(jdbc, inventarioService, reservaService);
        e.limpiar();
        e.inventario(EVENTO, 4, numerado(PLATEA, 5, 20), noNumerado(CAMPO, 100));
    }

    @Nested
    class ComputoDelLimite {

        @Test
        void cuentanLasPendientesVigentesYLasConfirmadasDeAmbosTiposDeSector() {
            e.reservarAsientos("101", EVENTO, PLATEA, 1, 1);
            UUID confirmada = e.reservarCantidad("101", EVENTO, CAMPO, 2);
            reservaService.confirmar(confirmada);

            assertThat(codigo(() -> e.reservarCantidad("101", EVENTO, CAMPO, 2))).isEqualTo("422 LIMITE_POR_CLIENTE");
            e.reservarCantidad("101", EVENTO, CAMPO, 1);
        }

        @Test
        void unaPendienteExpiradaNoCuentaAunqueElJobTodaviaNoLaVencio() {
            UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 4);
            e.expirar(id);

            e.reservarCantidad("101", EVENTO, CAMPO, 4);
            assertThat(e.estado(id)).isEqualTo("PENDIENTE");
        }

        @Test
        void noCuentanLasVencidasNiLasLiberadasNiLasCanceladas() {
            UUID vencida = e.reservarCantidad("101", EVENTO, CAMPO, 4);
            e.expirar(vencida);
            reservaService.vencerLote();
            UUID liberada = e.reservarCantidad("101", EVENTO, CAMPO, 4);
            reservaService.liberar(liberada);
            UUID cancelada = e.reservarCantidad("101", EVENTO, CAMPO, 4);
            reservaService.cancelar("101", cancelada);

            e.reservarCantidad("101", EVENTO, CAMPO, 4);
        }

        @Test
        void lasUnidadesDevueltasDejanDeContar() {
            UUID asientos = e.reservarAsientos("101", EVENTO, PLATEA, 1, 1, 1, 2);
            UUID cupos = e.reservarCantidad("101", EVENTO, CAMPO, 2);
            reservaService.confirmar(asientos);
            reservaService.confirmar(cupos);
            assertThat(codigo(() -> e.reservarCantidad("101", EVENTO, CAMPO, 1))).isEqualTo("422 LIMITE_POR_CLIENTE");

            reservaService.devolver(asientos, new DevolucionRequest(asientos(1, 2), null));
            reservaService.devolver(cupos, new DevolucionRequest(null, 1));

            e.reservarCantidad("101", EVENTO, CAMPO, 2);
            assertThat(codigo(() -> e.reservarCantidad("101", EVENTO, CAMPO, 1))).isEqualTo("422 LIMITE_POR_CLIENTE");
        }

        @Test
        void elLimiteEsPorClienteYPorEvento() {
            e.inventario(43L, 4, noNumerado(CAMPO, 100));
            e.reservarCantidad("101", EVENTO, CAMPO, 4);

            e.reservarCantidad("202", EVENTO, CAMPO, 4);
            e.reservarCantidad("101", 43L, CAMPO, 4);
        }

        @Test
        void usaElMaxPorClienteDelInventarioDelEvento() {
            e.inventario(43L, 2, noNumerado(CAMPO, 100));

            assertThat(codigo(() -> e.reservarCantidad("101", 43L, CAMPO, 3))).isEqualTo("422 LIMITE_POR_CLIENTE");
            e.reservarCantidad("101", 43L, CAMPO, 2);
        }

        @Test
        void superarElLimiteNoReservaNada() {
            e.reservarCantidad("101", EVENTO, CAMPO, 3);

            assertThat(codigo(() -> e.reservarAsientos("101", EVENTO, PLATEA, 2, 1, 2, 2)))
                    .isEqualTo("422 LIMITE_POR_CLIENTE");
            assertThat(e.entero("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE'")).isZero();
            assertThat(e.entero("SELECT count(*) FROM reserva")).isEqualTo(1);
        }
    }

    @Nested
    class CodigosDeError {

        @Test
        void creacion() {
            assertThat(codigo(() -> e.reservarCantidad("101", 999L, CAMPO, 1))).isEqualTo("404 NO_ENCONTRADO");
            assertThat(codigo(() -> e.reservarCantidad("101", EVENTO, 99L, 1))).isEqualTo("404 NO_ENCONTRADO");
            assertThat(codigo(() -> reservaService.crear("101", new ReservaRequest(EVENTO, PLATEA, null, 1))))
                    .isEqualTo("400 VALIDACION");
            e.inventario(43L, 4, noNumerado(CAMPO, 2));
            assertThat(codigo(() -> e.reservarCantidad("101", 43L, CAMPO, 3))).isEqualTo("409 CUPO_INSUFICIENTE");

            e.reservarAsientos("202", EVENTO, PLATEA, 1, 1);
            assertThat(codigo(() -> e.reservarAsientos("101", EVENTO, PLATEA, 1, 1)))
                    .isEqualTo("409 ASIENTO_NO_DISPONIBLE");

            inventarioService.cerrar(EVENTO);
            assertThat(codigo(() -> e.reservarCantidad("101", EVENTO, CAMPO, 1))).isEqualTo("409 EVENTO_NO_DISPONIBLE");
        }

        @Test
        void consultaYCancelacion() {
            UUID id = e.reservarCantidad("101", EVENTO, CAMPO, 1);

            assertThat(codigo(() -> reservaService.obtener("202", id))).isEqualTo("403 ACCESO_DENEGADO");
            assertThat(codigo(() -> reservaService.cancelar("202", id))).isEqualTo("403 ACCESO_DENEGADO");
            assertThat(codigo(() -> reservaService.obtener("101", UUID.randomUUID()))).isEqualTo("404 NO_ENCONTRADO");

            reservaService.confirmar(id);
            assertThat(codigo(() -> reservaService.cancelar("101", id))).isEqualTo("409 RESERVA_CONFIRMADA");
        }

        @Test
        void confirmacionYLiberacion() {
            UUID vencida = e.reservarCantidad("101", EVENTO, CAMPO, 1);
            e.expirar(vencida);
            assertThat(codigo(() -> reservaService.confirmar(vencida))).isEqualTo("410 RESERVA_VENCIDA");
            reservaService.vencerLote();
            assertThat(codigo(() -> reservaService.confirmar(vencida))).isEqualTo("410 RESERVA_VENCIDA");

            UUID liberada = e.reservarCantidad("102", EVENTO, CAMPO, 1);
            reservaService.liberar(liberada);
            assertThat(codigo(() -> reservaService.confirmar(liberada))).isEqualTo("409 ESTADO_INVALIDO");

            UUID confirmada = e.reservarCantidad("103", EVENTO, CAMPO, 1);
            reservaService.confirmar(confirmada);
            assertThat(codigo(() -> reservaService.liberar(confirmada))).isEqualTo("409 RESERVA_CONFIRMADA");

            assertThat(codigo(() -> reservaService.confirmar(UUID.randomUUID()))).isEqualTo("404 NO_ENCONTRADO");
            assertThat(codigo(() -> reservaService.liberar(UUID.randomUUID()))).isEqualTo("404 NO_ENCONTRADO");
        }

        @Test
        void devolucion() {
            UUID pendiente = e.reservarAsientos("101", EVENTO, PLATEA, 1, 1);
            assertThat(codigo(() -> reservaService.devolver(pendiente, new DevolucionRequest(asientos(1, 1), null))))
                    .isEqualTo("409 ESTADO_INVALIDO");

            reservaService.confirmar(pendiente);
            assertThat(codigo(() -> reservaService.devolver(pendiente, new DevolucionRequest(asientos(4, 4), null))))
                    .isEqualTo("409 ESTADO_INVALIDO");
            assertThat(codigo(() -> reservaService.devolver(pendiente, new DevolucionRequest(null, 1))))
                    .isEqualTo("400 VALIDACION");
            assertThat(codigo(() -> reservaService.devolver(UUID.randomUUID(), new DevolucionRequest(null, 1))))
                    .isEqualTo("404 NO_ENCONTRADO");
        }
    }

    /** 3.6 Una reserva con un lugar no disponible no deja ningun lugar tomado. */
    @Nested
    class TodoONada {

        @Test
        void siFallaElSegundoAsientoElPrimeroYElTerceroQuedanLibresSinCambios() {
            e.reservarAsientos("202", EVENTO, PLATEA, 3, 11);

            ApiException ex = catchThrowableOfType(ApiException.class,
                    () -> e.reservarAsientos("101", EVENTO, PLATEA, 3, 10, 3, 11, 3, 12));

            assertThat(ex.getCode()).isEqualTo("ASIENTO_NO_DISPONIBLE");
            assertThat(ex.getDetails()).containsExactly("F3-N11");
            assertAsientosIntactos(3, 10);
            assertAsientosIntactos(3, 12);
            assertNadaDelCliente("101");
        }

        @Test
        void siFallaElUltimoAsientoSeRevierteLoQueYaSeHabiaReservado() {
            e.reservarAsientos("202", EVENTO, PLATEA, 5, 10);

            ApiException ex = catchThrowableOfType(ApiException.class,
                    () -> e.reservarAsientos("101", EVENTO, PLATEA, 5, 10, 1, 1, 2, 2));

            assertThat(ex.getDetails()).containsExactly("F5-N10");
            assertAsientosIntactos(1, 1);
            assertAsientosIntactos(2, 2);
            assertNadaDelCliente("101");
        }

        @Test
        void informaTodosLosAsientosEnConflictoOrdenados() {
            e.reservarAsientos("202", EVENTO, PLATEA, 4, 2);
            UUID vendida = e.reservarAsientos("203", EVENTO, PLATEA, 2, 7);
            reservaService.confirmar(vendida);

            ApiException ex = catchThrowableOfType(ApiException.class,
                    () -> e.reservarAsientos("101", EVENTO, PLATEA, 4, 2, 1, 5, 2, 7));

            assertThat(ex.getDetails()).containsExactly("F2-N7", "F4-N2");
            assertAsientosIntactos(1, 5);
            assertThat(e.estadoAsiento(EVENTO, PLATEA, 2, 7)).isEqualTo("VENDIDO");
            assertNadaDelCliente("101");
        }

        @Test
        void unAsientoInexistenteResponde400YNoReservaLosDemas() {
            ApiException ex = catchThrowableOfType(ApiException.class,
                    () -> e.reservarAsientos("101", EVENTO, PLATEA, 1, 1, 9, 9));

            assertThat(ex.getCode()).isEqualTo("VALIDACION");
            assertThat(ex.getDetails()).containsExactly("F9-N9");
            assertAsientosIntactos(1, 1);
            assertNadaDelCliente("101");
        }

        @Test
        void cupoInsuficienteNoCambiaElCupoNiCreaLaReserva() {
            e.inventario(43L, 4, noNumerado(CAMPO, 10));
            e.reservarCantidad("202", 43L, CAMPO, 4);
            e.reservarCantidad("203", 43L, CAMPO, 3);

            assertThat(codigo(() -> e.reservarCantidad("101", 43L, CAMPO, 4))).isEqualTo("409 CUPO_INSUFICIENTE");
            assertThat(e.disponibles(43L, CAMPO)).isEqualTo(3);
            assertNadaDelCliente("101");
        }

        private void assertAsientosIntactos(int fila, int numero) {
            var asiento = jdbc.queryForMap("SELECT estado, reserva_id, version FROM asiento "
                    + "WHERE evento_id = ? AND sector_id = ? AND fila = ? AND numero = ?", EVENTO, PLATEA, fila, numero);
            assertThat(asiento.get("estado")).isEqualTo("LIBRE");
            assertThat(asiento.get("reserva_id")).isNull();
            assertThat(((Number) asiento.get("version")).longValue())
                    .as("el UPDATE revertido no deja rastro en version").isZero();
        }

        private void assertNadaDelCliente(String clienteId) {
            assertThat(e.entero("SELECT count(*) FROM reserva WHERE cliente_id = ?", clienteId)).isZero();
            assertThat(e.entero("SELECT count(*) FROM reserva_item ri JOIN reserva r ON r.id = ri.reserva_id "
                    + "WHERE r.cliente_id = ?", clienteId)).isZero();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** "status CODIGO" de la ApiException que lanza la accion. */
    private static String codigo(ThrowingCallable accion) {
        ApiException ex = catchThrowableOfType(ApiException.class, accion);
        assertThat(ex).as("se esperaba una ApiException").isNotNull();
        return ex.getStatus().value() + " " + ex.getCode();
    }
}
