package com.ticketya.reservas.controller;

import com.ticketya.reservas.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** 3.1 Regla del rol del solicitante: solo CLIENTE reserva; sin headers de identidad, 400. */
class IdentidadTest {

    @Test
    void clienteConHeadersDevuelveSuId() {
        assertThat(Identidad.cliente("101", "CLIENTE")).isEqualTo("101");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMIN", "ACCESO", "cliente", "OTRO"})
    void otroRolEs403AccesoDenegado(String rol) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> Identidad.cliente("101", rol));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo("ACCESO_DENEGADO");
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, CLIENTE", "101, NULL", "NULL, NULL", "'  ', CLIENTE", "101, ''"}, nullValues = "NULL")
    void sinHeadersDeIdentidadEs400Validacion(String userId, String rol) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> Identidad.cliente(userId, rol));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("VALIDACION");
        assertThat(ex.getDetails()).isNotEmpty();
    }
}
