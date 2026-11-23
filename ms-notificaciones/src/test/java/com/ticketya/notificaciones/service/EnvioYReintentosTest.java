package com.ticketya.notificaciones.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.notificaciones.IntegracionTest;
import com.ticketya.notificaciones.model.Destinatario;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Grupo 5 de punta a punta, con el procesador real (cada 100 ms) y esperas de reintento de 200 ms y 400 ms.
 */
@ExtendWith(OutputCaptureExtension.class)
class EnvioYReintentosTest extends IntegracionTest {

    @Test
    @DisplayName("Envio simulado por email: Envio exitoso al primer intento")
    void envioExitosoAlPrimerIntento(CapturedOutput salida) throws Exception {
        String respuesta = postInterno(pedidoCompra("cliente@example.com", null), "corr-envio-1")
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(respuesta).get("id").asText();

        Notificacion n = esperarEstado(id, EstadoNotificacion.ENVIADA);

        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getEnviadaEn()).isNotNull();
        assertThat(n.getUltimoError()).isNull();
        await().atMost(ESPERA_MAXIMA).untilAsserted(() -> assertThat(salida.getOut()).containsPattern(
                "EMAIL SIMULADO enviado a cliente@example.com .*tipo=COMPRA_CONFIRMADA.*correlationId=corr-envio-1"));
    }

    @Test
    @DisplayName("Reintentos con espera creciente: Exito en el segundo intento")
    void exitoEnElSegundoIntento() throws Exception {
        String id = aceptar(pedidoCompra("falla-una-vez@ticketya.test", null));

        Notificacion n = esperarEstado(id, EstadoNotificacion.ENVIADA);

        assertThat(n.getIntentos()).isEqualTo(2);
        assertThat(canal.llamadas(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Notificacion fallida al agotar los intentos: Intentos agotados")
    void intentosAgotados() throws Exception {
        String id = aceptar(pedidoCompra("falla@ticketya.test", null));

        Notificacion n = esperarEstado(id, EstadoNotificacion.FALLIDA);

        assertThat(n.getIntentos()).isEqualTo(3);
        assertThat(n.getUltimoError()).isNotBlank();
        assertThat(n.getProximoIntentoEn()).isNull();
        // Varios ciclos despues, sigue en 3 intentos: no hay un cuarto.
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() ->
                canal.llamadas(id) == 3 && repository.findById(id).orElseThrow().getIntentos() == 3);
    }

    @Test
    @DisplayName("Aislamiento de fallas del envio: Pedido para un destinatario que falla")
    void pedidoParaUnDestinatarioQueFalla() throws Exception {
        String falla = postInterno(pedidoCompra("falla@ticketya.test", null))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String normal = postInterno(pedidoCompra("cliente@example.com", null))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        JsonNode a = json.readTree(falla);
        JsonNode b = json.readTree(normal);
        assertThat(a.get("estado").asText()).isEqualTo("PENDIENTE").isEqualTo(b.get("estado").asText());
        assertThat(a.fieldNames()).toIterable().containsExactlyElementsOf(() -> b.fieldNames());
    }

    @Test
    @DisplayName("Envios pendientes tras un reinicio: Pendiente guardada antes de arrancar")
    void pendienteGuardadaAntesDeArrancar() {
        // Equivale a una notificacion que quedo PENDIENTE en la base cuando el servicio se detuvo.
        Instant antes = Instant.now().minusSeconds(60);
        Notificacion n = new Notificacion();
        n.setTipo(TipoNotificacion.EVENTO_CANCELADO);
        n.setDestinatario(new Destinatario("202", "cliente@example.com"));
        n.setDatos(Map.of("eventoId", 42));
        n.setEstado(EstadoNotificacion.PENDIENTE);
        n.setIntentos(1);
        n.setUltimoError("caida anterior");
        n.setCorrelationId("corr-reinicio");
        n.setCreadaEn(antes);
        n.setActualizadaEn(antes);
        n.setProximoIntentoEn(antes);
        String id = mongo.insert(n).getId();

        Notificacion enviada = esperarEstado(id, EstadoNotificacion.ENVIADA);

        assertThat(enviada.getIntentos()).isEqualTo(2);
    }
}
