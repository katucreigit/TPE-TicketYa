package com.ticketya.ventas.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.exception.ApiException;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReservasClientTest {

    private static final String BASE = "http://ms-reservas";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockRestServiceServer server;
    private ReservasClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ReservasClient(builder.build(), new ObjectMapper());
    }

    private static String reservaJson(String estado, String expiraEn) {
        return """
                {"reservaId":"%s","eventoId":7,"sectorId":3,"clienteId":"cli-100","estado":"%s",
                 "cantidad":2,"items":[{"fila":3,"numero":10},{"fila":3,"numero":11}],"expiraEn":"%s"}
                """.formatted(ID, estado, expiraEn);
    }

    private static String errorJson(int status, String code, String message) {
        return """
                {"status":%d,"code":"%s","message":"%s"}
                """.formatted(status, code, message);
    }

    // ---- validar ----

    @Test
    void validarReservaPendienteVigenteDelClienteDevuelveLaReserva() {
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "cli-100"))
                .andExpect(header("X-User-Rol", "CLIENTE"))
                .andRespond(withSuccess(reservaJson("PENDIENTE", "2026-11-10T17:05:00"), MediaType.APPLICATION_JSON));

        ReservaRemota r = client.validar(ID, "cli-100");

        assertEquals(ID, r.reservaId());
        assertEquals("cli-100", r.clienteId());
        assertEquals(2, r.items().size());
        server.verify();
    }

    @Test
    void validarReservaInformadaVencidaResponde410AunqueExpiraEnParezcaFuturo() {
        // ms-reservas informa VENCIDA (PENDIENTE expirada segun su base): decide el estado, no expiraEn.
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withSuccess(reservaJson("VENCIDA", "2099-01-01T00:00:00"), MediaType.APPLICATION_JSON));

        GoneException ex = assertThrows(GoneException.class, () -> client.validar(ID, "cli-100"));
        assertEquals("RESERVA_VENCIDA", ex.getCode());
    }

    @Test
    void validarReservaPendienteNoComparaExpiraEnContraUnRelojPropio() {
        // Regresion: expiraEn llega en el huso de la JVM de ms-reservas, sin offset (p. ej. UTC-3 => 3 h "atrasado"
        // respecto de UTC). Si ms-reservas la informa PENDIENTE, sigue vigente y no debe responder 410.
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withSuccess(reservaJson("PENDIENTE", "2026-11-10T12:10:00"), MediaType.APPLICATION_JSON));

        ReservaRemota r = assertDoesNotThrow(() -> client.validar(ID, "cli-100"));
        assertEquals("PENDIENTE", r.estado());
    }

    @Test
    void validarReservaVencidaResponde410() {
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withSuccess(reservaJson("VENCIDA", "2026-11-10T16:00:00"), MediaType.APPLICATION_JSON));

        assertThrows(GoneException.class, () -> client.validar(ID, "cli-100"));
    }

    @Test
    void validarReservaLiberadaOConfirmadaResponde409() {
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withSuccess(reservaJson("LIBERADA", "2026-11-10T17:05:00"), MediaType.APPLICATION_JSON));
        ConflictException liberada = assertThrows(ConflictException.class, () -> client.validar(ID, "cli-100"));
        assertEquals("ESTADO_INVALIDO", liberada.getCode());

        server.reset();
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withSuccess(reservaJson("CONFIRMADA", "2026-11-10T17:05:00"), MediaType.APPLICATION_JSON));
        assertThrows(ConflictException.class, () -> client.validar(ID, "cli-100"));
    }

    @Test
    void validarReservaDeOtroClienteResponde403() {
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(403, "ACCESO_DENEGADO", "No tiene permiso")));

        ApiException ex = assertThrows(ForbiddenException.class, () -> client.validar(ID, "cli-100"));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void validarReservaInexistenteResponde404() {
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(404, "NO_ENCONTRADO", "La reserva no existe")));

        assertThrows(NotFoundException.class, () -> client.validar(ID, "cli-100"));
    }

    // ---- confirmar ----

    @Test
    void confirmarInvocaElEndpointInterno() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/confirmar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> client.confirmar(ID));
        server.verify();
    }

    @Test
    void confirmarReservaVencidaResponde410() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/confirmar"))
                .andRespond(withStatus(HttpStatus.GONE).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(410, "RESERVA_VENCIDA", "La reserva vencio")));

        assertThrows(GoneException.class, () -> client.confirmar(ID));
    }

    @Test
    void confirmarReservaLiberadaResponde409() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/confirmar"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(409, "ESTADO_INVALIDO", "La reserva fue liberada")));

        ConflictException ex = assertThrows(ConflictException.class, () -> client.confirmar(ID));
        assertEquals("ESTADO_INVALIDO", ex.getCode());
    }

    // ---- liberar ----

    @Test
    void liberarInvocaElEndpointInterno() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/liberar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> client.liberar(ID));
        server.verify();
    }

    @Test
    void liberarReservaConfirmadaResponde409() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/liberar"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(409, "RESERVA_CONFIRMADA", "Una reserva confirmada no se puede cancelar")));

        ConflictException ex = assertThrows(ConflictException.class, () -> client.liberar(ID));
        assertEquals("RESERVA_CONFIRMADA", ex.getCode());
    }

    // ---- devolver ----

    @Test
    void devolverAsientoEnviaSoloItems() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/devolver"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"items\":[{\"fila\":3,\"numero\":10}]}", true))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> client.devolver(ID, ReservasClient.DevolucionLugares.asiento(3, 10)));
        server.verify();
    }

    @Test
    void devolverCupoEnviaSoloCantidad() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/devolver"))
                .andExpect(content().json("{\"cantidad\":1}", true))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> client.devolver(ID, ReservasClient.DevolucionLugares.cupo(1)));
        server.verify();
    }

    @Test
    void devolverEnReservaNoConfirmadaResponde409() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/devolver"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(409, "ESTADO_INVALIDO", "Solo se pueden devolver lugares de una reserva confirmada")));

        assertThrows(ConflictException.class,
                () -> client.devolver(ID, ReservasClient.DevolucionLugares.asiento(3, 10)));
    }

    // ---- fallas de comunicacion ----

    @Test
    void errorDelServicioSeInformaComoBadGateway() {
        server.expect(requestTo(BASE + "/internal/reservas/" + ID + "/confirmar"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        BadGatewayException ex = assertThrows(BadGatewayException.class, () -> client.confirmar(ID));
        assertEquals(502, ex.getStatus().value());
    }

    @Test
    void faltaDeConexionOTimeoutSeInformaComoBadGateway() {
        server.expect(requestTo(BASE + "/reservas/" + ID))
                .andRespond(withException(new IOException("Read timed out")));

        assertThrows(BadGatewayException.class, () -> client.validar(ID, "cli-100"));
    }
}

