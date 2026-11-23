package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.IntegracionTest;
import com.ticketya.recomendaciones.MsEventosSimulado.Modo;
import com.ticketya.recomendaciones.trazabilidad.CapturaLogs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catálogo con fallback (spec "Catálogo con fallback", design.md D5), con ms-eventos simulado y reloj controlable. */
class CatalogoFallbackTest extends IntegracionTest {

    private CapturaLogs logs;

    @BeforeEach
    void capturar() {
        logs = new CapturaLogs(CatalogoEventos.class);
    }

    @AfterEach
    void soltar() {
        logs.close();
    }

    @Test
    @DisplayName("Catálogo con fallback: Catálogo disponible")
    void catalogoDisponible() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);

        recomendaciones("101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradado").value(false))
                .andExpect(jsonPath("$.eventos[0].eventoId").value(42));
        assertThat(MS_EVENTOS.pedidos()).singleElement()
                .satisfies(p -> assertThat(p.query()).containsEntry("size", "100").containsEntry("page", "0"));
    }

    @ParameterizedTest(name = "ms-eventos {0}")
    @EnumSource(value = Modo.class, names = {"ERROR_500", "CORTE"})
    @DisplayName("Catálogo con fallback: ms-eventos caído con copia en memoria")
    void caidoConCopiaEnMemoria(Modo falla) throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        recomendaciones("101").andExpect(jsonPath("$.degradado").value(false));

        reloj.avanzar(Duration.ofSeconds(31));
        MS_EVENTOS.modo(falla);

        recomendaciones("101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradado").value(true))
                .andExpect(jsonPath("$.eventos[0].eventoId").value(42));
        assertThat(logs.mensajes("CATALOGO_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l).contains("copiaEnMemoria=true"));
    }

    @Test
    @DisplayName("Catálogo con fallback: ms-eventos caído sin copia")
    void caidoSinCopia() throws Exception {
        MS_EVENTOS.modo(Modo.ERROR_500);
        compro("101", 42L);
        compro("102", 42L, 43L);

        recomendaciones("101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradado").value(true))
                .andExpect(jsonPath("$.eventos").isEmpty());
        tambienCompraron(42)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(42))
                .andExpect(jsonPath("$.degradado").value(true))
                .andExpect(jsonPath("$.eventos").isEmpty());
    }

    @Test
    @DisplayName("Catálogo con fallback: ms-eventos detenido (conexión rechazada) sin copia")
    void detenidoSinCopia() throws Exception {
        MS_EVENTOS.modo(Modo.CORTE);

        recomendaciones("101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradado").value(true));
    }

    @Test
    void dentroDelTtlNoSeVuelveAConsultar() throws Exception {
        MS_EVENTOS.publicado(42, "RECITAL", "Tandil", FECHA);
        recomendaciones("101");
        reloj.avanzar(Duration.ofSeconds(29));
        MS_EVENTOS.modo(Modo.ERROR_500);

        recomendaciones("101")
                .andExpect(jsonPath("$.degradado").value(false))
                .andExpect(jsonPath("$.eventos[0].eventoId").value(42));
        assertThat(MS_EVENTOS.pedidos()).hasSize(1);
    }

    @Test
    void unaPaginaFallidaNoReemplazaLaCopia() throws Exception {
        MS_EVENTOS.publicado(1, "RECITAL", "Tandil", FECHA);
        recomendaciones("101").andExpect(jsonPath("$.eventos.length()").value(1));

        // 150 eventos: dos paginas; la segunda falla, asi que no se guarda una copia parcial.
        for (long id = 2; id <= 150; id++) {
            MS_EVENTOS.publicado(id, "TEATRO", "Azul", FECHA);
        }
        MS_EVENTOS.modo(Modo.FALLA_SEGUNDA_PAGINA);
        reloj.avanzar(Duration.ofSeconds(31));

        recomendaciones("101")
                .andExpect(jsonPath("$.degradado").value(true))
                .andExpect(jsonPath("$.eventos.length()").value(1));
    }

    @Test
    void leeTodasLasPaginasYSoloLosPublicados() throws Exception {
        for (long id = 1; id <= 150; id++) {
            MS_EVENTOS.publicado(id, "TEATRO", "Azul", FECHA);
        }
        MS_EVENTOS.evento(151, "TEATRO", "AGOTADO", "Azul", FECHA);

        CatalogoEventos.Resultado r = catalogo.obtener();

        assertThat(r.degradado()).isFalse();
        assertThat(r.eventos()).hasSize(150);
        assertThat(MS_EVENTOS.pedidos()).extracting(p -> p.query().get("page")).containsExactly("0", "1");
    }
}
