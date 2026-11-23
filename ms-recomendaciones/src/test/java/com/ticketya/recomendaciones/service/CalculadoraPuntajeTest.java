package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.client.EventoCatalogo;
import com.ticketya.recomendaciones.model.Preferencias;
import com.ticketya.recomendaciones.model.TipoEvento;
import com.ticketya.recomendaciones.service.CalculadoraPuntaje.Motivo;
import com.ticketya.recomendaciones.service.CalculadoraPuntaje.Puntuado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias del puntaje (spec "Puntaje de recomendación", design.md D3). Sin Spring ni base. */
class CalculadoraPuntajeTest {

    private static final LocalDateTime FECHA = LocalDateTime.parse("2027-03-01T21:00:00");

    @Test
    @DisplayName("Puntaje de recomendación: Coincidencia con todas las preferencias")
    void coincidenciaConTodasLasPreferencias() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(
                preferencias(List.of(TipoEvento.RECITAL), List.of("Los Andariegos"), "Tandil"));

        Puntuado p = calc.puntuar(evento(42, "RECITAL", "tandil", FECHA, "LOS ANDARIEGOS"), 0, 0, 0, 0);

        assertThat(p.puntaje()).isEqualTo(8.0);
        assertThat(p.motivos()).containsExactly(Motivo.TIPO, Motivo.ARTISTA, Motivo.CIUDAD);
    }

    @Test
    @DisplayName("Puntaje de recomendación: Popularidad relativa")
    void popularidadRelativa() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(sinPreferencias());

        Puntuado e42 = calc.puntuar(evento(42, "RECITAL", "Tandil", FECHA), 4, 4, 0, 0);
        Puntuado e43 = calc.puntuar(evento(43, "RECITAL", "Tandil", FECHA), 2, 4, 0, 0);

        assertThat(e42.puntaje()).isEqualTo(1.0);
        assertThat(e43.puntaje()).isEqualTo(0.5);
        assertThat(e42.motivos()).containsExactly(Motivo.POPULAR);
    }

    @Test
    @DisplayName("Puntaje de recomendación: Compradores en común")
    void compradoresEnComun() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(sinPreferencias());

        Puntuado e42 = calc.puntuar(evento(42, "TEATRO", "Azul", FECHA), 0, 0, 3, 3);
        Puntuado e43 = calc.puntuar(evento(43, "TEATRO", "Azul", FECHA), 0, 0, 1, 3);

        assertThat(e42.puntaje()).isEqualTo(2.0);
        assertThat(e43.puntaje()).isEqualTo(0.67);
        assertThat(e43.motivos()).containsExactly(Motivo.TAMBIEN_COMPRARON);
    }

    @Test
    void todosLosComponentesSuman() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(
                preferencias(List.of(TipoEvento.TEATRO), List.of("Soda"), "Azul"));

        Puntuado p = calc.puntuar(evento(1, "TEATRO", "Azul", FECHA, "Soda"), 1, 2, 2, 2);

        assertThat(p.puntaje()).isEqualTo(10.5);   // 3 + 3 + 2 + 2 + 0.5
        assertThat(p.motivos()).containsExactly(Motivo.TIPO, Motivo.ARTISTA, Motivo.CIUDAD,
                Motivo.TAMBIEN_COMPRARON, Motivo.POPULAR);
    }

    @Test
    void sinCoincidenciasPuntajeCeroYSinMotivos() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(
                preferencias(List.of(TipoEvento.DEPORTE), List.of("Otro"), "Rosario"));

        Puntuado p = calc.puntuar(evento(1, "TEATRO", "Azul", FECHA, "Soda"), 0, 3, 0, 0);

        assertThat(p.puntaje()).isEqualTo(0.0);
        assertThat(p.motivos()).isEmpty();
    }

    @Test
    void ciudadNulaDelEventoNoCoincide() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(preferencias(List.of(), List.of(), "Tandil"));

        assertThat(calc.puntuar(evento(1, "TEATRO", null, FECHA), 0, 0, 0, 0).puntaje()).isEqualTo(0.0);
    }

    @Test
    void redondeoADosDecimales() {
        assertThat(CalculadoraPuntaje.redondear(2.0 / 3)).isEqualTo(0.67);
        assertThat(CalculadoraPuntaje.redondear(0.125)).isEqualTo(0.13);
        assertThat(CalculadoraPuntaje.redondear(1.0 / 3)).isEqualTo(0.33);
    }

    @Test
    void ordenPorPuntajeLuegoFechaLuegoId() {
        CalculadoraPuntaje calc = new CalculadoraPuntaje(sinPreferencias());
        Puntuado alto = calc.puntuar(evento(9, "TEATRO", "Azul", FECHA.plusDays(30)), 2, 2, 0, 0);
        Puntuado tempranoId5 = calc.puntuar(evento(5, "TEATRO", "Azul", FECHA), 0, 2, 0, 0);
        Puntuado tempranoId3 = calc.puntuar(evento(3, "TEATRO", "Azul", FECHA), 0, 2, 0, 0);
        Puntuado tarde = calc.puntuar(evento(1, "TEATRO", "Azul", FECHA.plusDays(1)), 0, 2, 0, 0);
        Puntuado sinFecha = calc.puntuar(evento(2, "TEATRO", "Azul", null), 0, 2, 0, 0);

        List<Puntuado> ordenados = new ArrayList<>(List.of(tarde, sinFecha, tempranoId5, alto, tempranoId3));
        ordenados.sort(CalculadoraPuntaje.ORDEN);

        assertThat(ordenados).extracting(p -> p.evento().id()).containsExactly(9L, 3L, 5L, 1L, 2L);
    }

    // ------------------------------------------------------------------ utilidades

    private static Preferencias preferencias(List<TipoEvento> tipos, List<String> artistas, String ciudad) {
        return new Preferencias("101", new ArrayList<>(tipos), new ArrayList<>(artistas), ciudad, null);
    }

    private static Preferencias sinPreferencias() {
        return preferencias(List.of(), List.of(), null);
    }

    private static EventoCatalogo evento(long id, String tipo, String ciudad, LocalDateTime fecha, String... participantes) {
        return new EventoCatalogo(id, "Evento " + id, tipo, "PUBLICADO", ciudad, fecha, List.of(participantes));
    }
}
