package com.ticketya.ventas.trazabilidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.NotificacionesClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.service.CompraService;
import com.ticketya.ventas.service.PasarelaPagoService;
import com.ticketya.ventas.service.ReconciliacionPagosJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * add-trazabilidad (spec trazabilidad) aplicada a ms-ventas: correlationId, registro de cada solicitud y registro
 * de operaciones criticas. HTTP real (MockMvc), servicios reales y PostgreSQL real; solo se reemplazan los clientes
 * HTTP remotos. La propagacion saliente se prueba aparte, contra servidores HTTP reales, en
 * PropagacionCorrelacionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TrazabilidadTest {

    private static final long EVENTO = 42L;
    private static final long SECTOR = 11L;
    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2028, 6, 15, 21, 0);
    private static final BigDecimal PRECIO = new BigDecimal("45000.00");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PasarelaPagoService pasarelaPagoService;
    @Autowired
    private ReconciliacionPagosJob reconciliacionPagosJob;
    @Autowired
    private RegistroOperaciones registro;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ReservasClient reservasClient;
    @MockitoBean
    private EventosClient eventosClient;
    @MockitoBean
    private NotificacionesClient notificacionesClient;

    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE reintegro, devolucion, entrada, pago, compra CASCADE");
        pasarelaPagoService.limpiarSimulaciones();
        reset(reservasClient, eventosClient, notificacionesClient);
        when(eventosClient.obtenerSnapshotParaCompra(EVENTO, SECTOR))
                .thenReturn(new SnapshotCompra(EVENTO, SECTOR, FECHA_EVENTO, PRECIO));
        logs = new CapturaLogs(RegistroSolicitudesFilter.class, RegistroOperaciones.class);
    }

    @AfterEach
    void cerrar() {
        logs.close();
        MDC.clear();
    }

    // ------------------------------------------------------------------ identificador de correlacion

    @Nested
    @DisplayName("Identificador de correlacion compartido")
    class IdentificadorDeCorrelacion {

        @Test
        @DisplayName("Solicitud sin identificador de correlacion: se genera uno y relaciona sus registros")
        void sinCorrelationIdSeGeneraUnoYRelacionaLosRegistros() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 1);

            String generado = comprar("101", "key-gen-1", reservaId, "tok_ok", null)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getHeader(Correlacion.HEADER);

            assertThat(generado).isNotBlank();
            assertThat(UUID.fromString(generado)).isNotNull();
            assertThat(logs.campos("HTTP_REQUEST")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo(generado));
            assertThat(logs.operaciones("COMPRA_APROBADA")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo(generado));
        }

        @Test
        @DisplayName("Solicitud con identificador de correlacion: se conserva en la respuesta y en los registros")
        void conCorrelationIdSeConservaEnLaRespuestaYEnLosRegistros() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 1);

            comprar("101", "key-cons-1", reservaId, "tok_ok", "compra-abc-123")
                    .andExpect(status().isCreated())
                    .andExpect(header().string(Correlacion.HEADER, "compra-abc-123"));

            assertThat(logs.campos("HTTP_REQUEST")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo("compra-abc-123"));
            assertThat(logs.operaciones("COMPRA_APROBADA")).singleElement()
                    .satisfies(c -> assertThat(c.get("correlationId")).isEqualTo("compra-abc-123"));
            // El MDC tambien lo tiene: el patron de log lo agrega a cada linea del servicio.
            assertThat(logs.eventos("OPERACION_CRITICA")).singleElement()
                    .satisfies(ev -> assertThat(ev.getMDCPropertyMap())
                            .containsEntry(Correlacion.MDC_CORRELATION_ID, "compra-abc-123"));
        }

        @Test
        void unEndpointInternoTambienConservaElCorrelationIdRecibido() throws Exception {
            mvc.perform(post("/internal/eventos/{e}/cancelacion", EVENTO)
                            .header(Correlacion.HEADER, "cancel-ev-42"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(Correlacion.HEADER, "cancel-ev-42"));

            assertThat(logs.campos("HTTP_REQUEST")).singleElement()
                    .satisfies(c -> assertThat(c)
                            .containsEntry("endpoint", "/internal/eventos/42/cancelacion")
                            .containsEntry("correlationId", "cancel-ev-42"));
        }

        @Test
        void unCorrelationIdConSaltosDeLineaODemasiadoLargoSeReemplazaPorUnoGenerado() throws Exception {
            String conSalto = mvc.perform(get("/compras")
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .header(Correlacion.HEADER, "abc\nOPERACION_CRITICA operacion=FALSA"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(Correlacion.HEADER);

            String demasiadoLargo = mvc.perform(get("/compras")
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .header(Correlacion.HEADER, "x".repeat(129)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(Correlacion.HEADER);

            assertThat(UUID.fromString(conSalto)).isNotNull();
            assertThat(UUID.fromString(demasiadoLargo)).isNotNull();
            assertThat(logs.mensajes("OPERACION_CRITICA")).isEmpty();
        }

        @Test
        void elMdcQuedaLimpioAlTerminarLaSolicitud() throws Exception {
            mvc.perform(get("/compras/{id}", UUID.randomUUID())
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .header(Correlacion.HEADER, "corr-mdc"))
                    .andExpect(status().isNotFound());

            assertThat(MDC.get(Correlacion.MDC_CORRELATION_ID)).isNull();
            assertThat(MDC.get(Correlacion.MDC_USUARIO)).isNull();
            assertThat(MDC.get(Correlacion.MDC_ROL)).isNull();
        }
    }

    // ------------------------------------------------------------------ registro de solicitudes

    @Nested
    @DisplayName("Registro de solicitudes HTTP")
    class RegistroDeSolicitudes {

        @Test
        @DisplayName("Registro de una solicitud HTTP: metodo, ruta, usuario, rol, codigo, tiempo y correlationId")
        void registraMetodoRutaUsuarioRolCodigoTiempoYCorrelationId() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 1);

            comprar("101", "key-http-1", reservaId, "tok_ok", "corr-http-1")
                    .andExpect(status().isCreated());

            assertThat(logs.campos("HTTP_REQUEST")).singleElement().satisfies(c -> {
                assertThat(c).containsEntry("method", "POST")
                        .containsEntry("endpoint", "/compras")
                        .containsEntry("user", "101")
                        .containsEntry("rol", "CLIENTE")
                        .containsEntry("status", "201")
                        .containsEntry("correlationId", "corr-http-1");
                assertThat(Long.parseLong(c.get("durationMs"))).isNotNegative();
            });
        }

        @Test
        void registraLasSolicitudesSinIdentidadComoAnonimo() throws Exception {
            mvc.perform(post("/internal/eventos/{e}/cancelacion", EVENTO))
                    .andExpect(status().isOk());

            assertThat(logs.campos("HTTP_REQUEST")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("method", "POST")
                    .containsEntry("endpoint", "/internal/eventos/42/cancelacion")
                    .containsEntry("user", "ANONIMO")
                    .containsEntry("rol", "ANONIMO")
                    .containsEntry("status", "200"));
        }

        @Test
        void registraElCodigoDeLasRespuestasDeError() throws Exception {
            mvc.perform(get("/compras").header("X-User-Id", "7").header("X-User-Rol", "ORGANIZADOR"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/compras/{id}", UUID.randomUUID())
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                    .andExpect(status().isNotFound());

            assertThat(logs.campos("HTTP_REQUEST"))
                    .extracting(c -> c.get("status") + " " + c.get("user") + " " + c.get("rol"))
                    .containsExactly("403 7 ORGANIZADOR", "404 101 CLIENTE");
        }
    }

    // ------------------------------------------------------------------ operaciones criticas

    @Nested
    @DisplayName("Registro centralizado de operaciones criticas")
    class OperacionesCriticas {

        @Test
        @DisplayName("Compra aprobada: correlationId, usuario e ids de negocio; repeticion idempotente no duplica")
        void compraAprobadaYRepeticionIdempotenteSinDuplicar() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 2);

            String body = comprar("101", "key-ok-1", reservaId, "tok_ok", "corr-compra")
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String compraId = json.readTree(body).get("compraId").asText();

            // Repeticion idempotente: responde 200 OK y no vuelve a registrar COMPRA_APROBADA.
            comprar("101", "key-ok-1", reservaId, "tok_ok", "corr-compra-rep")
                    .andExpect(status().isOk());

            assertThat(logs.operaciones("COMPRA_APROBADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-compra")
                    .containsEntry("compraId", compraId)
                    .containsEntry("reservaId", reservaId.toString())
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "90000.00")
                    .containsEntry("entradas", "2")
                    .containsKey("transaccion"));
        }

        @Test
        @DisplayName("Compra rechazada por la pasarela: se registra una sola vez")
        void compraRechazada() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 1);

            comprar("101", "key-rej-1", reservaId, "tok_rechazado", "corr-rej")
                    .andExpect(status().isPaymentRequired());
            // Reintento con misma clave devuelve 402 sin volver a registrar.
            comprar("101", "key-rej-1", reservaId, "tok_rechazado", "corr-rej-2")
                    .andExpect(status().isPaymentRequired());

            assertThat(logs.operaciones("COMPRA_APROBADA")).isEmpty();
            assertThat(logs.operaciones("COMPRA_RECHAZADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-rej")
                    .containsEntry("reservaId", reservaId.toString())
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00"));
        }

        @Test
        @DisplayName("Compra reembolsada cuando la reserva vence al confirmar: registra COMPRA_REEMBOLSADA")
        void compraReembolsadaPorReservaVencidaAlConfirmar() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 1);
            doThrow(new GoneException("Reserva vencida")).when(reservasClient).confirmar(reservaId);

            comprar("101", "key-remb-1", reservaId, "tok_ok", "corr-remb")
                    .andExpect(status().isGone());

            assertThat(logs.operaciones("COMPRA_APROBADA")).isEmpty();
            assertThat(logs.operaciones("COMPRA_REEMBOLSADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-remb")
                    .containsEntry("reservaId", reservaId.toString())
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00")
                    .containsKey("transaccion"));
        }

        @Test
        @DisplayName("Pago incierto y reconciliacion por job: correlationId propio del job y usuario SISTEMA")
        void pagoInciertoYReconciliacionPorJob() throws Exception {
            UUID reservaId = prepararReservaVigente("101", 1);

            comprar("101", "key-inc-1", reservaId, "tok_timeout", "corr-timeout")
                    .andExpect(status().isGatewayTimeout());

            assertThat(logs.operaciones("PAGO_INCIERTO")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-timeout")
                    .containsEntry("reservaId", reservaId.toString())
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00"));
            String compraId = logs.operaciones("PAGO_INCIERTO").get(0).get("compraId");

            pasarelaPagoService.resolverIncertidumbre(
                    CompraService.claveCobroDe(UUID.fromString(compraId)), EstadoPago.APROBADO);
            logs.limpiar();

            reconciliacionPagosJob.reconciliarPagosInciertos();

            assertThat(logs.operaciones("COMPRA_APROBADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", Correlacion.SISTEMA)
                    .containsEntry("compraId", compraId)
                    .containsEntry("reservaId", reservaId.toString())
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00"));
            assertThat(logs.operaciones("PAGO_RECONCILIADO")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", Correlacion.SISTEMA)
                    .containsEntry("compraId", compraId)
                    .containsEntry("resultado", "APROBADA"));

            String corrJob = logs.operaciones("PAGO_RECONCILIADO").get(0).get("correlationId");
            assertThat(UUID.fromString(corrJob)).isNotNull();
            assertThat(logs.operaciones("COMPRA_APROBADA").get(0).get("correlationId")).isEqualTo(corrJob);
            assertThat(MDC.get(Correlacion.MDC_CORRELATION_ID)).isNull();
        }

        @Test
        @DisplayName("Devolucion solicitada, aprobada y reintento tras falla remota: no se registra hasta completar")
        void devolucionSolicitadaYAprobadaConReintento() throws Exception {
            JsonNode compra = crearCompraAprobada("101", "key-dev-1", 1);
            String compraId = compra.get("compraId").asText();
            String entradaId = compra.get("entradas").get(0).get("entradaId").asText();
            logs.limpiar();

            String bodySol = mvc.perform(post("/devoluciones")
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .header(Correlacion.HEADER, "corr-dev-sol")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entradaId\":\"" + entradaId + "\",\"motivo\":\"No puedo asistir\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String devolucionId = json.readTree(bodySol).get("devolucionId").asText();

            assertThat(logs.operaciones("DEVOLUCION_SOLICITADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "101")
                    .containsEntry("correlationId", "corr-dev-sol")
                    .containsEntry("devolucionId", devolucionId)
                    .containsEntry("entradaId", entradaId)
                    .containsEntry("compraId", compraId)
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00"));

            // Primera aprobacion falla al liberar lugar en ms-reservas: no se registra DEVOLUCION_APROBADA.
            doThrow(new BadGatewayException("ms-reservas caido"))
                    .when(reservasClient).devolver(any(), any());
            mvc.perform(post("/devoluciones/{id}/aprobar", devolucionId)
                            .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN")
                            .header(Correlacion.HEADER, "corr-dev-falla"))
                    .andExpect(status().isBadGateway());
            assertThat(logs.operaciones("DEVOLUCION_APROBADA")).isEmpty();

            // Reintento exitoso: se registra una sola vez con el correlationId de la aprobacion exitosa.
            doNothing().when(reservasClient).devolver(any(), any());
            mvc.perform(post("/devoluciones/{id}/aprobar", devolucionId)
                            .header("X-User-Id", "admin-1").header("X-User-Rol", "ADMIN")
                            .header(Correlacion.HEADER, "corr-dev-ok"))
                    .andExpect(status().isOk());

            assertThat(logs.operaciones("DEVOLUCION_APROBADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "admin-1")
                    .containsEntry("correlationId", "corr-dev-ok")
                    .containsEntry("devolucionId", devolucionId)
                    .containsEntry("entradaId", entradaId)
                    .containsEntry("compraId", compraId)
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00")
                    .containsKey("reintegroId")
                    .containsKey("transaccion"));
        }

        @Test
        @DisplayName("Devolucion rechazada por administrador: registra DEVOLUCION_RECHAZADA")
        void devolucionRechazada() throws Exception {
            JsonNode compra = crearCompraAprobada("101", "key-dev-rej", 1);
            String compraId = compra.get("compraId").asText();
            String entradaId = compra.get("entradas").get(0).get("entradaId").asText();

            String bodySol = mvc.perform(post("/devoluciones")
                            .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entradaId\":\"" + entradaId + "\",\"motivo\":\"Error\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String devolucionId = json.readTree(bodySol).get("devolucionId").asText();
            logs.limpiar();

            mvc.perform(post("/devoluciones/{id}/rechazar", devolucionId)
                            .header("X-User-Id", "admin-2").header("X-User-Rol", "ADMIN")
                            .header(Correlacion.HEADER, "corr-dev-rej")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motivo\":\"No corresponde\"}"))
                    .andExpect(status().isOk());

            assertThat(logs.operaciones("DEVOLUCION_RECHAZADA")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", "admin-2")
                    .containsEntry("correlationId", "corr-dev-rej")
                    .containsEntry("devolucionId", devolucionId)
                    .containsEntry("entradaId", entradaId)
                    .containsEntry("compraId", compraId)
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101"));
        }

        @Test
        @DisplayName("Cancelacion de evento: registra reintegros y resumen con usuario SISTEMA; repeticion idempotente no duplica")
        void cancelacionDeEventoYRepeticionIdempotente() throws Exception {
            JsonNode compra = crearCompraAprobada("101", "key-cancel-1", 2);
            String compraId = compra.get("compraId").asText();
            String e1 = compra.get("entradas").get(0).get("entradaId").asText();
            String e2 = compra.get("entradas").get(1).get("entradaId").asText();
            logs.limpiar();

            mvc.perform(post("/internal/eventos/{e}/cancelacion", EVENTO)
                            .header(Correlacion.HEADER, "corr-cancel-42"))
                    .andExpect(status().isOk());
            // Repeticion idempotente sin cambios: no vuelve a registrar ni reintegros ni cancelacion.
            mvc.perform(post("/internal/eventos/{e}/cancelacion", EVENTO)
                            .header(Correlacion.HEADER, "corr-cancel-42-rep"))
                    .andExpect(status().isOk());

            assertThat(logs.operaciones("REINTEGRO_COMPLETADO"))
                    .extracting(c -> c.get("entradaId"))
                    .containsExactlyInAnyOrder(e1, e2);
            assertThat(logs.operaciones("REINTEGRO_COMPLETADO")).allSatisfy(c -> assertThat(c)
                    .containsEntry("usuario", Correlacion.SISTEMA)
                    .containsEntry("correlationId", "corr-cancel-42")
                    .containsEntry("tipo", "CANCELACION_EVENTO")
                    .containsEntry("compraId", compraId)
                    .containsEntry("eventoId", "42")
                    .containsEntry("clienteId", "101")
                    .containsEntry("monto", "45000.00")
                    .containsKey("reintegroId")
                    .containsKey("transaccion"));

            assertThat(logs.operaciones("CANCELACION_EVENTO")).singleElement().satisfies(c -> assertThat(c)
                    .containsEntry("usuario", Correlacion.SISTEMA)
                    .containsEntry("correlationId", "corr-cancel-42")
                    .containsEntry("eventoId", "42")
                    .containsEntry("entradasAfectadas", "2")
                    .containsEntry("reintegrosCompletados", "2")
                    .containsEntry("reintegrosPendientes", "0"));
        }

        @Test
        void unaOperacionDentroDeUnaTransaccionRevertidaNoQuedaRegistrada() {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(s -> {
                registro.registrar(RegistroOperaciones.Operacion.COMPRA_APROBADA,
                        RegistroOperaciones.datos("compraId", "revertida"));
                s.setRollbackOnly();
            });
            tx.executeWithoutResult(s -> registro.registrar(RegistroOperaciones.Operacion.COMPRA_APROBADA,
                    RegistroOperaciones.datos("compraId", "confirmada")));

            assertThat(logs.operaciones("COMPRA_APROBADA")).extracting(c -> c.get("compraId"))
                    .containsExactly("confirmada");
        }
    }

    // ------------------------------------------------------------------ seguimiento de punta a punta

    @Test
    @DisplayName("Seguimiento de una compra: compra y consultas comparten un unico correlationId")
    void seguimientoDeUnaCompraConUnUnicoCorrelationId() throws Exception {
        String corr = "compra-punta-a-punta";
        UUID reservaId = prepararReservaVigente("101", 1);

        String body = comprar("101", "key-e2e-1", reservaId, "tok_ok", corr)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode compra = json.readTree(body);
        String compraId = compra.get("compraId").asText();
        String entradaId = compra.get("entradas").get(0).get("entradaId").asText();

        mvc.perform(get("/compras/{id}", compraId)
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header(Correlacion.HEADER, corr))
                .andExpect(status().isOk());
        mvc.perform(get("/entradas/{id}", entradaId)
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE")
                        .header(Correlacion.HEADER, corr))
                .andExpect(status().isOk());

        assertThat(logs.campos("HTTP_REQUEST")).hasSize(3)
                .allSatisfy(c -> assertThat(c).containsEntry("correlationId", corr));
        assertThat(logs.operaciones("COMPRA_APROBADA")).singleElement().satisfies(c -> assertThat(c)
                .containsEntry("compraId", compraId)
                .containsEntry("correlationId", corr));
    }

    // ------------------------------------------------------------------ auxiliares

    private UUID prepararReservaVigente(String clienteId, int cantidad) {
        UUID reservaId = UUID.randomUUID();
        List<ReservaRemota.Item> items = cantidad == 1
                ? List.of(new ReservaRemota.Item(1, 1))
                : List.of(new ReservaRemota.Item(1, 1), new ReservaRemota.Item(1, 2));
        ReservaRemota reserva = new ReservaRemota(
                reservaId, EVENTO, SECTOR, clienteId, "PENDIENTE", cantidad, items,
                LocalDateTime.now().plusMinutes(10));
        when(reservasClient.validar(eq(reservaId), eq(clienteId))).thenReturn(reserva);
        when(reservasClient.obtener(eq(reservaId), eq(clienteId))).thenReturn(reserva);
        return reservaId;
    }

    private JsonNode crearCompraAprobada(String clienteId, String key, int cantidad) throws Exception {
        UUID reservaId = prepararReservaVigente(clienteId, cantidad);
        String body = comprar(clienteId, key, reservaId, "tok_ok", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private ResultActions comprar(String clienteId, String idempotencyKey, UUID reservaId,
                                  String token, String correlationId) throws Exception {
        var req = post("/compras")
                .header("X-User-Id", clienteId)
                .header("X-User-Rol", "CLIENTE")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reservaId": "%s",
                          "medioPago": {"tipo": "TARJETA_CREDITO", "token": "%s", "ultimos4": "4242"}
                        }""".formatted(reservaId, token));
        if (correlationId != null) {
            req.header(Correlacion.HEADER, correlationId);
        }
        return mvc.perform(req);
    }
}
