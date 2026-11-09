package com.ticketya.reservas.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada codigo del contrato sale por el GlobalExceptionHandler con su status y el formato Error. */
class CodigoErrorTest {

    private static final Map<String, Supplier<ApiException>> ERRORES = Map.of(
            "validacion", () -> CodigoError.validacion("Datos invalidos", List.of("filas debe ser mayor a 0")),
            "noEncontrado", () -> CodigoError.noEncontrado("No existe"),
            "accesoDenegado", CodigoError::accesoDenegado,
            "estadoInvalido", () -> CodigoError.estadoInvalido("Ya existe"),
            "asientoNoDisponible", () -> CodigoError.asientoNoDisponible(
                    List.of(CodigoError.asiento(3, 10), CodigoError.asiento(3, 11))),
            "cupoInsuficiente", CodigoError::cupoInsuficiente,
            "eventoNoDisponible", CodigoError::eventoNoDisponible,
            "reservaConfirmada", CodigoError::reservaConfirmada,
            "reservaVencida", CodigoError::reservaVencida,
            "limitePorCliente", () -> CodigoError.limitePorCliente(4, 3));

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new LanzadorController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of("validacion", 400, "VALIDACION"),
                Arguments.of("accesoDenegado", 403, "ACCESO_DENEGADO"),
                Arguments.of("noEncontrado", 404, "NO_ENCONTRADO"),
                Arguments.of("estadoInvalido", 409, "ESTADO_INVALIDO"),
                Arguments.of("asientoNoDisponible", 409, "ASIENTO_NO_DISPONIBLE"),
                Arguments.of("cupoInsuficiente", 409, "CUPO_INSUFICIENTE"),
                Arguments.of("eventoNoDisponible", 409, "EVENTO_NO_DISPONIBLE"),
                Arguments.of("reservaConfirmada", 409, "RESERVA_CONFIRMADA"),
                Arguments.of("reservaVencida", 410, "RESERVA_VENCIDA"),
                Arguments.of("limitePorCliente", 422, "LIMITE_POR_CLIENTE"));
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @MethodSource("casos")
    void respondeConElStatusYElCodigoDelContrato(String error, int status, String code) throws Exception {
        mvc.perform(get("/lanzar/" + error))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value("/lanzar/" + error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @org.junit.jupiter.api.Test
    void asientoNoDisponibleListaLosAsientosEnConflictoEnDetails() throws Exception {
        mvc.perform(get("/lanzar/asientoNoDisponible"))
                .andExpect(jsonPath("$.details[0]").value("F3-N10"))
                .andExpect(jsonPath("$.details[1]").value("F3-N11"))
                .andExpect(jsonPath("$.message").value("Los asientos F3-N10, F3-N11 ya no estan disponibles"));
    }

    @org.junit.jupiter.api.Test
    void limitePorClienteUsaElMensajeDelContrato() throws Exception {
        mvc.perform(get("/lanzar/limitePorCliente"))
                .andExpect(jsonPath("$.message").value("Maximo 4 entradas por cliente para este evento (ya tiene 3)"));
    }

    @RestController
    static class LanzadorController {

        @GetMapping("/lanzar/{error}")
        void lanzar(@PathVariable String error) {
            throw ERRORES.get(error).get();
        }
    }
}
