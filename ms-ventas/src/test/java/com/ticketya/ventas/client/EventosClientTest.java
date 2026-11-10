package com.ticketya.ventas.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EventosClientTest {

    private static final String BASE = "http://ms-eventos";
    private static final String SECTORES_JSON = """
            [
              {"sectorId": 11, "nombre": "Platea Alta", "tipo": "NUMERADO", "precio": 45000.00, "filas": 20, "asientosPorFila": 30},
              {"sectorId": 12, "nombre": "Campo", "tipo": "NO_NUMERADO", "precio": 28000.00, "capacidad": 5000}
            ]""";

    private MockRestServiceServer server;
    private EventosClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EventosClient(builder.build(), new ObjectMapper().findAndRegisterModules());
    }

    private static String eventoJson(String estado) {
        return """
                {"id": 42, "nombre": "Festival", "tipo": "RECITAL", "estado": "%s",
                 "organizadorId": 7, "lugarId": 3, "fechaHora": "2027-01-15T21:00:00",
                 "participantes": [], "maxEntradasPorCliente": 4, "altaDemanda": false}
                """.formatted(estado);
    }

    @Test
    void obtenerSnapshotDeEventoPublicadoDevuelveFechaYPrecioDelSector() {
        server.expect(requestTo(BASE + "/eventos/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(eventoJson("PUBLICADO"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/eventos/42/sectores"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SECTORES_JSON, MediaType.APPLICATION_JSON));

        SnapshotCompra snapshot = client.obtenerSnapshotParaCompra(42L, 11L);

        assertEquals(42L, snapshot.eventoId());
        assertEquals(11L, snapshot.sectorId());
        assertEquals(LocalDateTime.of(2027, 1, 15, 21, 0), snapshot.fechaHoraEvento());
        assertEquals(new BigDecimal("45000.00"), snapshot.precioUnitario());
        server.verify();
    }

    @Test
    void obtenerSnapshotDeEventoAgotadoTambienEsValidoParaReservaExistente() {
        server.expect(requestTo(BASE + "/eventos/42"))
                .andRespond(withSuccess(eventoJson("AGOTADO"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/eventos/42/sectores"))
                .andRespond(withSuccess(SECTORES_JSON, MediaType.APPLICATION_JSON));

        SnapshotCompra snapshot = client.obtenerSnapshotParaCompra(42L, 12L);

        assertEquals(new BigDecimal("28000.00"), snapshot.precioUnitario());
    }

    @Test
    void eventoSuspendidoOCanceladoResponde409EventoNoDisponible() {
        server.expect(requestTo(BASE + "/eventos/42"))
                .andRespond(withSuccess(eventoJson("SUSPENDIDO"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/eventos/42/sectores"))
                .andRespond(withSuccess(SECTORES_JSON, MediaType.APPLICATION_JSON));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> client.obtenerSnapshotParaCompra(42L, 11L));
        assertEquals("EVENTO_NO_DISPONIBLE", ex.getCode());
    }

    @Test
    void sectorSinPrecioEnElEventoResponde404() {
        server.expect(requestTo(BASE + "/eventos/42"))
                .andRespond(withSuccess(eventoJson("PUBLICADO"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/eventos/42/sectores"))
                .andRespond(withSuccess(SECTORES_JSON, MediaType.APPLICATION_JSON));

        assertThrows(NotFoundException.class, () -> client.obtenerSnapshotParaCompra(42L, 99L));
    }

    @Test
    void eventoInexistenteResponde404() {
        server.expect(requestTo(BASE + "/eventos/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":404,"code":"EVENTO_NO_ENCONTRADO","message":"No existe"}"""));

        assertThrows(NotFoundException.class, () -> client.obtenerEvento(999L));
    }

    @Test
    void errorDelServicioOFallaDeConexionSeInformaComoBadGateway502() {
        server.expect(requestTo(BASE + "/eventos/42"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        BadGatewayException ex500 = assertThrows(BadGatewayException.class, () -> client.obtenerEvento(42L));
        assertEquals(502, ex500.getStatus().value());

        server.reset();
        server.expect(requestTo(BASE + "/eventos/42"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThrows(BadGatewayException.class, () -> client.obtenerEvento(42L));
    }
}

