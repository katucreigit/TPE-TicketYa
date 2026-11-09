package com.ticketya.reservas.service;

import com.ticketya.reservas.dto.AsientoRef;
import com.ticketya.reservas.dto.ReservaRequest;
import com.ticketya.reservas.exception.ApiException;
import com.ticketya.reservas.model.enums.TipoSector;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** 3.1 Reglas de reserva y del limite, y el codigo de error que produce cada una. Sin base ni Spring. */
class ReglasReservaTest {

    private static final AsientoRef F3_N10 = new AsientoRef(3, 10);
    private static final AsientoRef F3_N11 = new AsientoRef(3, 11);

    @Nested
    class FormaDelPedido {

        @Test
        void pedidoConAsientosEsNumerado() {
            assertThat(ReglasReserva.validarPedido(pedido(List.of(F3_N10, F3_N11), null))).isTrue();
        }

        @Test
        void pedidoConCantidadEsNoNumerado() {
            assertThat(ReglasReserva.validarPedido(pedido(null, 3))).isFalse();
        }

        @Test
        void asientosYCantidadJuntosEs400() {
            assertValidacion(() -> ReglasReserva.validarPedido(pedido(List.of(F3_N10), 1)));
        }

        @Test
        void sinAsientosNiCantidadEs400() {
            assertValidacion(() -> ReglasReserva.validarPedido(pedido(null, null)));
            assertValidacion(() -> ReglasReserva.validarPedido(pedido(List.of(), null)));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        void cantidadMenorOIgualACeroEs400(int cantidad) {
            assertThat(assertValidacion(() -> ReglasReserva.validarPedido(pedido(null, cantidad))).getDetails())
                    .containsExactly("cantidad debe ser mayor a 0");
        }

        @Test
        void asientoRepetidoEs400ConElAsientoEnDetails() {
            assertThat(assertValidacion(() -> ReglasReserva.validarPedido(
                    pedido(List.of(F3_N10, F3_N11, F3_N10, F3_N10), null))).getDetails())
                    .containsExactly("asientos repite el asiento F3-N10");
        }
    }

    @Nested
    class TipoDeSector {

        @Test
        void cantidadEnSectorNumeradoEs400() {
            assertValidacion(() -> ReglasReserva.validarTipoDeSector(TipoSector.NUMERADO, false));
        }

        @Test
        void asientosEnSectorNoNumeradoEs400() {
            assertValidacion(() -> ReglasReserva.validarTipoDeSector(TipoSector.NO_NUMERADO, true));
        }

        @Test
        void combinacionesValidasNoFallan() {
            assertThatCode(() -> ReglasReserva.validarTipoDeSector(TipoSector.NUMERADO, true)).doesNotThrowAnyException();
            assertThatCode(() -> ReglasReserva.validarTipoDeSector(TipoSector.NO_NUMERADO, false))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Limite {

        @ParameterizedTest(name = "ya tiene {0}, pide {1}, maximo {2}")
        @CsvSource({"0, 4, 4", "3, 1, 4", "0, 1, 1", "5, 1, 6"})
        void llegarJustoAlMaximoEstaPermitido(long contadas, int pedidas, int maximo) {
            assertThatCode(() -> ReglasReserva.verificarLimite(contadas, pedidas, maximo)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "ya tiene {0}, pide {1}, maximo {2}")
        @CsvSource({"3, 2, 4", "0, 5, 4", "4, 1, 4", "1, 1, 1"})
        void superarElMaximoEs422LimitePorCliente(long contadas, int pedidas, int maximo) {
            assertThatThrownBy(() -> ReglasReserva.verificarLimite(contadas, pedidas, maximo))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException ex = (ApiException) e;
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                        assertThat(ex.getCode()).isEqualTo("LIMITE_POR_CLIENTE");
                    });
        }

        @Test
        void elMensajeInformaElMaximoYLoQueYaTiene() {
            assertThatThrownBy(() -> ReglasReserva.verificarLimite(3, 2, 4))
                    .hasMessage("Maximo 4 entradas por cliente para este evento (ya tiene 3)");
        }
    }

    @Nested
    class OrdenDeLosUpdate {

        @Test
        void losAsientosSeOrdenanPorFilaYDespuesPorNumero() {
            List<AsientoRef> pedidos = List.of(new AsientoRef(5, 1), new AsientoRef(2, 9), new AsientoRef(2, 3),
                    new AsientoRef(10, 2), new AsientoRef(2, 10));

            assertThat(ReglasReserva.ordenados(pedidos)).containsExactly(new AsientoRef(2, 3), new AsientoRef(2, 9),
                    new AsientoRef(2, 10), new AsientoRef(5, 1), new AsientoRef(10, 2));
        }

        @Test
        void dosPedidosConLosMismosAsientosEnDistintoOrdenQuedanIguales() {
            List<AsientoRef> a = List.of(F3_N11, new AsientoRef(1, 1), F3_N10);
            List<AsientoRef> b = List.of(F3_N10, F3_N11, new AsientoRef(1, 1));

            assertThat(ReglasReserva.ordenados(a)).isEqualTo(ReglasReserva.ordenados(b));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ReservaRequest pedido(List<AsientoRef> asientos, Integer cantidad) {
        return new ReservaRequest(42L, 11L, asientos, cantidad);
    }

    /** Verifica que la accion lance 400 VALIDACION y devuelve la excepcion para revisar details. */
    private static ApiException assertValidacion(ThrowingCallable accion) {
        ApiException ex = catchThrowableOfType(ApiException.class, accion);
        assertThat(ex).as("se esperaba una ApiException").isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("VALIDACION");
        return ex;
    }
}
