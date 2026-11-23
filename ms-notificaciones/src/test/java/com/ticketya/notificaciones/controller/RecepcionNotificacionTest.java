package com.ticketya.notificaciones.controller;

import com.ticketya.notificaciones.IntegracionTest;
import com.ticketya.notificaciones.model.Destinatario;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Grupo 3: POST /internal/notificaciones (spec notificaciones, requisitos de recepcion, tipos, validacion y correlacion). */
class RecepcionNotificacionTest extends IntegracionTest {

    @Nested
    @DisplayName("Recepcion de pedidos de notificacion")
    class Recepcion {

        @Test
        @DisplayName("Pedido valido")
        void pedidoValido() throws Exception {
            canal.bloquear(); // el documento queda PENDIENTE con 0 intentos mientras se verifica

            String respuesta = postInterno(pedidoCompra("cliente@example.com", null))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andReturn().getResponse().getContentAsString();

            Notificacion n = repository.findById(json.readTree(respuesta).get("id").asText()).orElseThrow();
            assertThat(n.getTipo()).isEqualTo(TipoNotificacion.COMPRA_CONFIRMADA);
            assertThat(n.getDestinatario()).isEqualTo(new Destinatario("101", "cliente@example.com"));
            assertThat(arbol(n.getDatos())).isEqualTo(arbol(Map.of("compraId", 55, "total", 30000)));
            assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
            assertThat(n.getIntentos()).isZero();
            assertThat(n.getCreadaEn()).isNotNull();
        }

        @Test
        @DisplayName("La respuesta no espera al envio")
        void laRespuestaNoEsperaAlEnvio() throws Exception {
            canal.bloquear();
            String primera = aceptar(pedidoCompra("cliente@example.com", null));
            // El procesador ya tomo la primera y quedo detenido dentro del envio.
            await().atMost(ESPERA_MAXIMA).until(() -> canal.llamadas(primera) == 1);

            long inicio = System.nanoTime();
            postInterno(pedidoCompra("otro@example.com", null))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"));
            Duration demora = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(demora).isLessThan(Duration.ofSeconds(2));
            assertThat(repository.findById(primera).orElseThrow().getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        }
    }

    @Nested
    @DisplayName("Tipos de notificacion admitidos")
    class Tipos {

        @ParameterizedTest
        @EnumSource(TipoNotificacion.class)
        @DisplayName("Cada tipo admitido se acepta")
        void cadaTipoAdmitidoSeAcepta(TipoNotificacion tipo) throws Exception {
            String id = aceptar(pedido(tipo.name(), "101", "cliente@example.com", Map.of("eventoId", 42), null));
            assertThat(repository.findById(id).orElseThrow().getTipo()).isEqualTo(tipo);
        }

        @Test
        @DisplayName("Datos con forma distinta segun el tipo")
        void datosConFormaDistintaSegunElTipo() throws Exception {
            Map<String, Object> datos = Map.of("eventoId", 42, "cambios",
                    Map.of("fecha", Map.of("antes", "2026-12-01T21:00", "despues", "2026-12-08T21:00")));

            String id = aceptar(pedido("EVENTO_MODIFICADO", "101", "cliente@example.com", datos, null));

            assertThat(arbol(repository.findById(id).orElseThrow().getDatos()))
                    .isEqualTo(arbol(datos));
        }

        @Test
        @DisplayName("Pedido sin datos")
        void pedidoSinDatos() throws Exception {
            String cuerpo = pedido("EVENTO_CANCELADO", "101", "cliente@example.com", null, null);
            postInterno(cuerpo).andExpect(status().isAccepted());

            assertThat(repository.findAll()).singleElement()
                    .satisfies(n -> assertThat(n.getDatos()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Validacion del pedido")
    class Validacion {

        @Test
        @DisplayName("Tipo desconocido")
        void tipoDesconocido() throws Exception {
            rechazado(pedido("OFERTA_ESPECIAL", "101", "cliente@example.com", null, null));
        }

        @Test
        @DisplayName("Tipo ausente")
        void tipoAusente() throws Exception {
            rechazado(pedido(null, "101", "cliente@example.com", null, null));
        }

        @Test
        @DisplayName("Destinatario sin usuario")
        void destinatarioSinUsuario() throws Exception {
            rechazado(pedido("COMPRA_CONFIRMADA", null, "cliente@example.com", null, null));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"no-es-un-email", ""})
        @DisplayName("Email invalido o ausente")
        void emailInvalidoOAusente(String email) throws Exception {
            rechazado(pedido("COMPRA_CONFIRMADA", "101", email, null, null));
        }

        private void rechazado(String cuerpo) throws Exception {
            postInterno(cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"))
                    .andExpect(jsonPath("$.status").value(400));
            assertThat(contar()).isZero();
        }
    }

    @Nested
    @DisplayName("Identificador de correlacion de la notificacion")
    @ExtendWith(OutputCaptureExtension.class)
    class Correlacion {

        @Test
        @DisplayName("Pedido con correlationId")
        void pedidoConCorrelationId(CapturedOutput salida) throws Exception {
            String respuesta = postInterno(pedidoCompra("cliente@example.com", null), "abc-123")
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("X-Correlation-Id", "abc-123"))
                    .andReturn().getResponse().getContentAsString();
            String id = json.readTree(respuesta).get("id").asText();

            assertThat(repository.findById(id).orElseThrow().getCorrelationId()).isEqualTo("abc-123");
            esperarEstado(id, EstadoNotificacion.ENVIADA);
            await().atMost(ESPERA_MAXIMA).untilAsserted(() -> assertThat(salida.getOut())
                    .containsPattern("\\[abc-123\\].*EMAIL SIMULADO enviado a cliente@example.com.*correlationId=abc-123"));
        }

        @Test
        @DisplayName("Pedido sin correlationId")
        void pedidoSinCorrelationId() throws Exception {
            String id = aceptar(pedidoCompra("cliente@example.com", null));
            assertThat(repository.findById(id).orElseThrow().getCorrelationId()).isNotBlank();
        }
    }
}
