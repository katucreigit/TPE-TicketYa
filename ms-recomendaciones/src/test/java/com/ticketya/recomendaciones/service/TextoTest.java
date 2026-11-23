package com.ticketya.recomendaciones.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Normalizacion para comparar artistas y ciudades (design.md D3). */
class TextoTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Tandil|tandil",
            "  tandil |tandil",
            "LOS ANDARIEGOS|los andariegos",
            "Córdoba|cordoba",
            "Ñandú  Rock|nandu rock",
            "José   María|jose maria"
    })
    void normaliza(String entrada, String esperado) {
        assertThat(Texto.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    void nuloEsVacio() {
        assertThat(Texto.normalizar(null)).isEmpty();
    }
}
