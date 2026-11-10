package com.ticketya.ventas.controller;

import com.ticketya.ventas.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class IdentidadTest {

    @Test
    void clienteConHeadersDevuelveSuId() {
        assertThat(Identidad.cliente("cli-100", "CLIENTE")).isEqualTo("cli-100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMIN", "ACCESO", "cliente", "OTRO"})
    void otroRolEs403AccesoDenegado(String rol) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> Identidad.cliente("cli-100", rol));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo("ACCESO_DENEGADO");
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, CLIENTE", "cli-100, NULL", "NULL, NULL", "'  ', CLIENTE", "cli-100, ''"}, nullValues = "NULL")
    void sinHeadersDeIdentidadEs400Validacion(String userId, String rol) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> Identidad.cliente(userId, rol));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("VALIDACION");
        assertThat(ex.getDetails()).isNotEmpty();
    }

    @Test
    void idempotencyKeyValidaDevuelveLaClaveLimpia() {
        assertThat(Identidad.idempotencyKey("  k-100  ")).isEqualTo("k-100");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void sinIdempotencyKeyEs400IdempotencyKeyRequerida(String key) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> Identidad.idempotencyKey(key));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUERIDA");
    }
}

