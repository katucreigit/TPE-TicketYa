package com.ticketya.ventas.recomendaciones;

import com.ticketya.ventas.client.RecomendacionesClient;
import com.ticketya.ventas.trazabilidad.CapturaLogs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.ticketya.ventas.recomendaciones.ServiciosSimulados.AVISOS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** RecomendacionesClient es best-effort: nunca lanza, y ante una falla deja la advertencia. Sin Spring. */
class RecomendacionesClientTest {

    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        ServiciosSimulados.reiniciar();
        logs = new CapturaLogs(RecomendacionesClient.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
        ServiciosSimulados.reiniciar();
    }

    @Test
    void enviaLaCompraConElFormatoDelContrato() {
        cliente().registrarCompra("c-1", "101", 42L);

        assertThat(AVISOS).singleElement().satisfies(a -> {
            assertThat(a.ruta()).isEqualTo("/internal/compras");
            assertThat(a.cuerpo().replace(" ", "")).isEqualTo("{\"compraId\":\"c-1\",\"clienteId\":\"101\",\"eventoIds\":[42]}");
        });
        assertThat(logs.mensajes("RECOMENDACIONES_NO_DISPONIBLE")).isEmpty();
    }

    @Test
    void noLanzaAnteUn500() {
        ServiciosSimulados.statusRecomendaciones = 500;

        assertThatCode(() -> cliente().registrarCompra("c-2", "101", 42L)).doesNotThrowAnyException();
        assertThat(logs.campos("RECOMENDACIONES_NO_DISPONIBLE")).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("compraId", "c-2").containsEntry("eventoId", "42"));
    }

    @Test
    void noLanzaSinServicio() {
        RecomendacionesClient sinServicio = new RecomendacionesClient(
                RestClient.builder().baseUrl("http://127.0.0.1:1").build());

        assertThatCode(() -> sinServicio.registrarCompra("c-3", "101", 42L)).doesNotThrowAnyException();
        assertThat(logs.mensajes("RECOMENDACIONES_NO_DISPONIBLE")).hasSize(1);
    }

    private static RecomendacionesClient cliente() {
        return new RecomendacionesClient(RestClient.builder()
                .baseUrl(ServiciosSimulados.url(ServiciosSimulados.MS_RECOMENDACIONES)).build());
    }
}
