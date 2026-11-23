package com.ticketya.ventas.client;

import com.ticketya.ventas.client.NotificacionesClient.TipoNotificacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class NotificacionesClientTest {

    private static final String BASE = "http://ms-notificaciones";

    private MockRestServiceServer server;
    private NotificacionesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NotificacionesClient(builder.build(), "ticketya.local");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void enviarCompraConfirmadaCon202EnviaPayloadExactoYPropagaCorrelationId() {
        MDC.put("correlationId", "corr-123");
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-Id", "corr-123"))
                .andExpect(jsonPath("$.tipo").value("COMPRA_CONFIRMADA"))
                .andExpect(jsonPath("$.destinatario.usuarioId").value("cli-100"))
                .andExpect(jsonPath("$.destinatario.email").value("cliente@example.com"))
                .andExpect(jsonPath("$.claveDeduplicacion").value("compra-confirmada-c1"))
                .andExpect(jsonPath("$.datos.compraId").value("c1"))
                .andExpect(jsonPath("$.datos.eventoId").value(42))
                .andExpect(jsonPath("$.datos.total").value(90000))
                .andExpect(jsonPath("$.datos.entradas", hasSize(1)))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"id":"notif-1","estado":"PENDIENTE"}"""));

        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA,
                "cli-100",
                "cliente@example.com",
                Map.of(
                        "compraId", "c1",
                        "eventoId", 42L,
                        "total", 90000,
                        "entradas", List.of(Map.of("entradaId", "e1"))
                ),
                "compra-confirmada-c1"));

        server.verify();
    }

    @Test
    void enviarEventoCanceladoOmiteCorrelationIdCuandoFaltaEnMdcYTruncaClaveLarga() {
        String clienteLargo = "cli-" + "a".repeat(250);
        String claveLarga = "evento-cancelado-42-" + clienteLargo;
        String claveEsperada = claveLarga.substring(0, 200);

        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("X-Correlation-Id"))
                .andExpect(jsonPath("$.tipo").value("EVENTO_CANCELADO"))
                .andExpect(jsonPath("$.destinatario.usuarioId").value(clienteLargo))
                .andExpect(jsonPath("$.claveDeduplicacion").value(claveEsperada))
                .andExpect(jsonPath("$.datos.eventoId").value(42))
                .andExpect(jsonPath("$.datos.entradasAfectadas").value(2))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.EVENTO_CANCELADO,
                clienteLargo,
                null,
                Map.of("eventoId", 42L, "entradasAfectadas", 2),
                claveLarga));

        server.verify();
    }

    @Test
    void resolucionDeEmailCubreExplicitoValidoInvalidoClienteIdEmailYClienteIdSimple() {
        // 1) Explicito invalido -> fallback determinista desde usuarioId
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andExpect(jsonPath("$.destinatario.email").value("101@ticketya.local"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.enviar(TipoNotificacion.COMPRA_CONFIRMADA, "101", "no-es-un-email",
                Map.of("compraId", "c1"), "k1");

        // 2) Sin email explicito pero usuarioId ya es email -> usa usuarioId
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andExpect(jsonPath("$.destinatario.email").value("ana@ticketya.com"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.enviar(TipoNotificacion.COMPRA_CONFIRMADA, "ana@ticketya.com", null,
                Map.of("compraId", "c2"), "k2");

        // 3) usuarioId con caracteres especiales -> sanitizado determinista
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andExpect(jsonPath("$.destinatario.email").value("cli-100-abc@ticketya.local"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.enviar(TipoNotificacion.COMPRA_CONFIRMADA, "cli 100/abc", null,
                Map.of("compraId", "c3"), "k3");

        server.verify();
    }

    @Test
    void erroresHttpYTimeoutsNoLanzanExcepciones() {
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA, "cli-100", null, Map.of(), "k-400"));

        server.reset();
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA, "cli-100", null, Map.of(), "k-500"));

        server.reset();
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA, "cli-100", null, Map.of(), "k-503"));

        server.reset();
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA, "cli-100", null, Map.of(), "k-timeout"));

        server.reset();
        server.expect(requestTo(BASE + "/internal/notificaciones"))
                .andRespond(withException(new IOException("Connection refused")));
        assertDoesNotThrow(() -> client.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA, "cli-100", null, Map.of(), "k-io"));
    }
}
