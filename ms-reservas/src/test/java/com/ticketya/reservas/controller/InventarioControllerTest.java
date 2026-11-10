package com.ticketya.reservas.controller;

import com.jayway.jsonpath.JsonPath;
import com.ticketya.reservas.TestcontainersConfiguration;
import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.model.Reserva;
import com.ticketya.reservas.model.enums.EstadoReserva;
import com.ticketya.reservas.repository.AsientoRepository;
import com.ticketya.reservas.repository.InventarioSectorRepository;
import com.ticketya.reservas.repository.ReservaRepository;
import com.ticketya.reservas.service.AvisoAgotadoService;
import com.ticketya.reservas.service.VencimientoJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Grupo 1 (inventario y disponibilidad) de punta a punta: HTTP, servicio y PostgreSQL real (Testcontainers).
 * Solo se reemplaza el cliente HTTP a ms-eventos, que es un servicio externo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventarioControllerTest {

    private static final long EVENTO = 42L;
    private static final long NUMERADO = 11L;
    private static final long CAMPO = 12L;

    private static final String INVENTARIO_42 = """
            {"eventoId": 42, "sectores": [
              {"sectorId": 11, "tipo": "NUMERADO", "filas": 20, "asientosPorFila": 30},
              {"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 5000}
            ]}""";

    private static final String UN_LUGAR_DE_CAMPO = """
            {"eventoId": 42, "sectorId": 12, "cantidad": 1}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private AsientoRepository asientoRepository;
    @Autowired
    private InventarioSectorRepository inventarioSectorRepository;
    @Autowired
    private AvisoAgotadoService avisoAgotadoService;
    @Autowired
    private VencimientoJob vencimientoJob;

    @MockitoBean
    private EventosClient eventosClient;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE reserva_item, reserva, asiento, inventario_sector, inventario_evento "
                + "RESTART IDENTITY CASCADE");
    }

    @Nested
    class Inicializacion {

        @Test
        void creaAsientosLibresYCupoYResponde201() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());

            assertThat(contar("SELECT count(*) FROM asiento WHERE evento_id = 42 AND sector_id = 11 "
                    + "AND estado = 'LIBRE' AND version = 0")).isEqualTo(600);
            assertThat(contar("SELECT count(*) FROM asiento WHERE sector_id = 12")).isZero();
            assertThat(contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12")).isEqualTo(5000);
            assertThat(contar("SELECT capacidad_total FROM inventario_sector WHERE sector_id = 11")).isEqualTo(600);
        }

        @Test
        void guardaElEventoAbiertoConMaxPorCliente4PorDefectoYSinAvisoNotificado() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());

            var fila = jdbc.queryForMap("SELECT abierto, max_por_cliente, agotado_notificado, "
                    + "creado_en <= localtimestamp AS creado_por_la_base FROM inventario_evento WHERE evento_id = 42");
            assertThat(fila.get("abierto")).isEqualTo(true);
            assertThat(fila.get("max_por_cliente")).isEqualTo(4);
            assertThat(fila.get("agotado_notificado")).isNull();
            assertThat(fila.get("creado_por_la_base")).isEqualTo(true);
            verifyNoInteractions(eventosClient);
        }

        @Test
        void respetaElMaxPorClienteInformado() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "maxPorCliente": 6, "sectores": [
                      {"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 10}]}""")
                    .andExpect(status().isCreated());

            assertThat(contar("SELECT max_por_cliente FROM inventario_evento WHERE evento_id = 42")).isEqualTo(6);
        }

        @Test
        void segundaInicializacionResponde409SinTocarAsientosNiReservas() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            Reserva reserva = crearReservaPendiente(NUMERADO, 1);
            reservarAsiento(1, 1, reserva.getId());

            crearInventario("""
                    {"eventoId": 42, "maxPorCliente": 9, "sectores": [
                      {"sectorId": 11, "tipo": "NUMERADO", "filas": 1, "asientosPorFila": 1},
                      {"sectorId": 13, "tipo": "NO_NUMERADO", "capacidad": 7}]}""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ESTADO_INVALIDO"))
                    .andExpect(jsonPath("$.path").value("/internal/inventarios"));

            assertThat(contar("SELECT count(*) FROM asiento WHERE evento_id = 42")).isEqualTo(600);
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado = 'RESERVADO'")).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM inventario_sector")).isEqualTo(2);
            assertThat(contar("SELECT max_por_cliente FROM inventario_evento WHERE evento_id = 42")).isEqualTo(4);
            assertThat(contar("SELECT count(*) FROM reserva WHERE estado = 'PENDIENTE'")).isEqualTo(1);
        }

        @Test
        void sectorInvalidoResponde400YNoCreaNadaNiSiquieraElPrimerSector() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "sectores": [
                      {"sectorId": 11, "tipo": "NUMERADO", "filas": 20, "asientosPorFila": 30},
                      {"sectorId": 12, "tipo": "NO_NUMERADO"}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"))
                    .andExpect(jsonPath("$.details", hasItem(
                            "sectores[1].capacidad debe ser mayor a 0 en un sector no numerado")));

            assertNadaCreado();
        }

        @Test
        void sectorNumeradoSinFilasOAsientosResponde400() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "sectores": [
                      {"sectorId": 11, "tipo": "NUMERADO", "filas": 0, "asientosPorFila": 30},
                      {"sectorId": 13, "tipo": "NUMERADO", "filas": 5}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"))
                    .andExpect(jsonPath("$.details.length()").value(2));

            assertNadaCreado();
        }

        @Test
        void sectorIdRepetidoResponde400() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "sectores": [
                      {"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 10},
                      {"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 20}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));

            assertNadaCreado();
        }

        @Test
        void maxPorClienteMenorA1Responde400() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "maxPorCliente": 0, "sectores": [
                      {"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 10}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));

            assertNadaCreado();
        }

        @Test
        void faltanCamposObligatoriosResponde400() throws Exception {
            crearInventario("""
                    {"sectores": []}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));

            assertNadaCreado();
        }
    }

    @Nested
    class Disponibilidad {

        @Test
        void devuelveLibresPorSectorSinContarReservadosNiVendidosYSinCache() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            Reserva pendiente = crearReservaPendiente(NUMERADO, 1);
            reservarAsiento(1, 1, pendiente.getId());
            UUID vendida = UUID.randomUUID();
            reservarAsiento(1, 2, vendida);
            tx.executeWithoutResult(s -> asientoRepository.venderDeReserva(vendida));
            tx.executeWithoutResult(s -> inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 790));

            mvc.perform(get("/eventos/42/disponibilidad"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.eventoId").value(42))
                    .andExpect(jsonPath("$.sectores.length()").value(2))
                    .andExpect(jsonPath("$.sectores[0].sectorId").value(11))
                    .andExpect(jsonPath("$.sectores[0].tipo").value("NUMERADO"))
                    .andExpect(jsonPath("$.sectores[0].capacidad").value(600))
                    .andExpect(jsonPath("$.sectores[0].disponibles").value(598))
                    .andExpect(jsonPath("$.sectores[1].sectorId").value(12))
                    .andExpect(jsonPath("$.sectores[1].tipo").value("NO_NUMERADO"))
                    .andExpect(jsonPath("$.sectores[1].capacidad").value(5000))
                    .andExpect(jsonPath("$.sectores[1].disponibles").value(4210));
        }

        @Test
        void eventoSinInventarioResponde404() throws Exception {
            mvc.perform(get("/eventos/999/disponibilidad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
        }
    }

    @Nested
    class MapaDeAsientos {

        @Test
        void devuelveAsientosOrdenadosPorFilaYNumero() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "sectores": [
                      {"sectorId": 11, "tipo": "NUMERADO", "filas": 2, "asientosPorFila": 2}]}""")
                    .andExpect(status().isCreated());
            reservarAsiento(2, 1, UUID.randomUUID());

            mvc.perform(get("/eventos/42/sectores/11/asientos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].fila").value(1)).andExpect(jsonPath("$[0].numero").value(1))
                    .andExpect(jsonPath("$[1].fila").value(1)).andExpect(jsonPath("$[1].numero").value(2))
                    .andExpect(jsonPath("$[2].fila").value(2)).andExpect(jsonPath("$[2].numero").value(1))
                    .andExpect(jsonPath("$[2].estado").value("RESERVADO"))
                    .andExpect(jsonPath("$[3].fila").value(2)).andExpect(jsonPath("$[3].numero").value(2));
        }

        @Test
        void filtraPorEstado() throws Exception {
            crearInventario("""
                    {"eventoId": 42, "sectores": [
                      {"sectorId": 11, "tipo": "NUMERADO", "filas": 2, "asientosPorFila": 2}]}""")
                    .andExpect(status().isCreated());
            reservarAsiento(1, 2, UUID.randomUUID());

            mvc.perform(get("/eventos/42/sectores/11/asientos").param("estado", "LIBRE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[*].estado", org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is("LIBRE"))));
        }

        @Test
        void sectorNoNumeradoResponde400() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());

            mvc.perform(get("/eventos/42/sectores/12/asientos"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
        }

        @Test
        void estadoInvalidoResponde400() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());

            mvc.perform(get("/eventos/42/sectores/11/asientos").param("estado", "ROTO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACION"));
        }

        @Test
        void sectorOEventoInexistenteResponde404() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());

            mvc.perform(get("/eventos/42/sectores/99/asientos"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
            mvc.perform(get("/eventos/999/sectores/11/asientos"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
        }
    }

    @Nested
    class CierreYReapertura {

        @Test
        void cierreLiberaLasReservasPendientesYDevuelveSusLugares() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            Reserva numerada = crearReservaPendiente(NUMERADO, 2);
            reservarAsiento(1, 1, numerada.getId());
            reservarAsiento(1, 2, numerada.getId());
            Reserva campo = crearReservaPendiente(CAMPO, 3);
            tx.executeWithoutResult(s -> inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 3));

            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());

            assertThat(estadoReserva(numerada.getId())).isEqualTo("LIBERADA");
            assertThat(estadoReserva(campo.getId())).isEqualTo("LIBERADA");
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado <> 'LIBRE' OR reserva_id IS NOT NULL"))
                    .isZero();
            assertThat(contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12")).isEqualTo(5000);
            assertThat(contar("SELECT count(*) FROM inventario_evento WHERE evento_id = 42 AND NOT abierto"))
                    .isEqualTo(1);
            verifyNoInteractions(eventosClient);
        }

        @Test
        void cierreRepetidoResponde200SinCambios() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            crearReservaPendiente(CAMPO, 3);
            tx.executeWithoutResult(s -> inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 3));

            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());
            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());

            assertThat(contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12")).isEqualTo(5000);
            assertThat(contar("SELECT version FROM inventario_sector WHERE sector_id = 12")).isEqualTo(2);
        }

        @Test
        void cierreNoTocaReservasConfirmadas() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            Reserva confirmada = crearReservaPendiente(NUMERADO, 1);
            reservarAsiento(3, 10, confirmada.getId());
            tx.executeWithoutResult(s -> {
                reservaRepository.confirmar(confirmada.getId());
                asientoRepository.venderDeReserva(confirmada.getId());
            });

            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());

            assertThat(estadoReserva(confirmada.getId())).isEqualTo("CONFIRMADA");
            assertThat(contar("SELECT count(*) FROM asiento WHERE estado = 'VENDIDO'")).isEqualTo(1);
        }

        @Test
        void cerrarOAbrirSinInventarioResponde404() throws Exception {
            mvc.perform(post("/internal/eventos/999/cerrar"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
            mvc.perform(post("/internal/eventos/999/abrir"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
        }

        @Test
        void reaperturaVuelveAAbrirElInventario() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());

            mvc.perform(post("/internal/eventos/42/abrir")).andExpect(status().isOk());

            assertThat(contar("SELECT count(*) FROM inventario_evento WHERE evento_id = 42 AND abierto"))
                    .isEqualTo(1);
            verify(eventosClient, never()).actualizarDisponibilidad(anyLong(), anyBoolean());
        }

        @Test
        void trasLaReaperturaLasNuevasReservasSeAceptanDeNuevo() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());
            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());
            reservarHttp("101", UN_LUGAR_DE_CAMPO)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVENTO_NO_DISPONIBLE"));

            mvc.perform(post("/internal/eventos/42/abrir")).andExpect(status().isOk());

            reservarHttp("101", UN_LUGAR_DE_CAMPO)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"));
            assertThat(contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12")).isEqualTo(4999);
        }

        @Test
        void reaperturaDeUnEventoSinLugaresAvisaAgotadoDespuesDelCommit() throws Exception {
            crearInventarioDeUnLugar();
            agotarUnicoLugar();
            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());

            mvc.perform(post("/internal/eventos/42/abrir")).andExpect(status().isOk());

            verify(eventosClient).actualizarDisponibilidad(EVENTO, true);
            assertThat(agotadoNotificado()).isTrue();
        }
    }

    @Nested
    class AvisoDeAgotado {

        @Test
        void ultimoLugarOcupadoAvisaAgotadoTrue() throws Exception {
            crearInventarioDeUnLugar();
            agotarUnicoLugar();

            avisoAgotadoService.evaluar(EVENTO);

            verify(eventosClient).actualizarDisponibilidad(EVENTO, true);
            assertThat(agotadoNotificado()).isTrue();
        }

        @Test
        void lugarLiberadoEnEventoAgotadoAvisaAgotadoFalse() throws Exception {
            crearInventarioDeUnLugar();
            agotarUnicoLugar();
            avisoAgotadoService.evaluar(EVENTO);
            tx.executeWithoutResult(s -> inventarioSectorRepository.sumarCupo(EVENTO, CAMPO, 1));

            avisoAgotadoService.evaluar(EVENTO);

            verify(eventosClient).actualizarDisponibilidad(EVENTO, false);
            assertThat(agotadoNotificado()).isFalse();
        }

        @Test
        void sinCambioDeEstadoNoAvisa() throws Exception {
            crearInventario(INVENTARIO_42).andExpect(status().isCreated());

            avisoAgotadoService.evaluar(EVENTO);
            vencimientoJob.ejecutar();

            verifyNoInteractions(eventosClient);
        }

        @Test
        void fallaDelAvisoConservaLaOperacionYElJobLoReintenta() throws Exception {
            crearInventarioDeUnLugar();
            Reserva reserva = crearReservaPendiente(CAMPO, 1);
            agotarUnicoLugar();
            doThrow(new ResourceAccessException("ms-eventos no responde"))
                    .doNothing()
                    .when(eventosClient).actualizarDisponibilidad(EVENTO, true);

            avisoAgotadoService.evaluar(EVENTO);

            assertThat(agotadoNotificado()).as("el campo no se actualiza si el aviso falla").isNull();
            assertThat(estadoReserva(reserva.getId())).isEqualTo("PENDIENTE");
            assertThat(contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12")).isZero();

            vencimientoJob.ejecutar();

            verify(eventosClient, times(2)).actualizarDisponibilidad(EVENTO, true);
            assertThat(agotadoNotificado()).isTrue();

            vencimientoJob.ejecutar();
            verify(eventosClient, times(2)).actualizarDisponibilidad(EVENTO, true);
        }

        @Test
        void eventoCerradoNoAvisa() throws Exception {
            crearInventarioDeUnLugar();
            agotarUnicoLugar();
            mvc.perform(post("/internal/eventos/42/cerrar")).andExpect(status().isOk());

            avisoAgotadoService.evaluar(EVENTO);
            vencimientoJob.ejecutar();

            verifyNoInteractions(eventosClient);
        }

        @Test
        void reservarElUltimoLugarAvisaAgotadoYCancelarloAvisaQueVuelveAHaber() throws Exception {
            crearInventarioDeUnLugar();

            String body = reservarHttp("101", UN_LUGAR_DE_CAMPO).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            verify(eventosClient).actualizarDisponibilidad(EVENTO, true);
            assertThat(agotadoNotificado()).isTrue();

            String reservaId = JsonPath.read(body, "$.reservaId");
            mvc.perform(delete("/reservas/" + reservaId).header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                    .andExpect(status().isNoContent());
            verify(eventosClient).actualizarDisponibilidad(EVENTO, false);
            assertThat(agotadoNotificado()).isFalse();
        }

        @Test
        void siFallaElAvisoAlReservarLaReservaSeConservaYElJobLoReintenta() throws Exception {
            crearInventarioDeUnLugar();
            doThrow(new ResourceAccessException("ms-eventos no responde"))
                    .doNothing()
                    .when(eventosClient).actualizarDisponibilidad(EVENTO, true);

            String body = reservarHttp("101", UN_LUGAR_DE_CAMPO).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            assertThat(estadoReserva(UUID.fromString(JsonPath.read(body, "$.reservaId")))).isEqualTo("PENDIENTE");
            assertThat(contar("SELECT disponibles FROM inventario_sector WHERE sector_id = 12")).isZero();
            assertThat(agotadoNotificado()).as("el campo no se actualiza si el aviso falla").isNull();

            vencimientoJob.ejecutar();

            verify(eventosClient, times(2)).actualizarDisponibilidad(EVENTO, true);
            assertThat(agotadoNotificado()).isTrue();
        }

        @Test
        void avisoSeMandaDespuesDelCommitYNoSiLaTransaccionSeRevierte() {
            crearInventarioDeUnLugarSinHttp();
            doNothing().when(eventosClient).actualizarDisponibilidad(anyLong(), anyBoolean());

            try {
                tx.executeWithoutResult(s -> {
                    inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 1);
                    avisoAgotadoService.evaluarDespuesDelCommit(EVENTO);
                    throw new IllegalStateException("falla de la operacion");
                });
            } catch (IllegalStateException esperado) {
                // la operacion se revirtio
            }
            verifyNoInteractions(eventosClient);

            tx.executeWithoutResult(s -> {
                inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 1);
                avisoAgotadoService.evaluarDespuesDelCommit(EVENTO);
                verifyNoInteractions(eventosClient);
            });
            verify(eventosClient).actualizarDisponibilidad(EVENTO, true);
        }
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions reservarHttp(String clienteId, String json) throws Exception {
        return mvc.perform(post("/reservas").header("X-User-Id", clienteId).header("X-User-Rol", "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions crearInventario(String json) throws Exception {
        return mvc.perform(post("/internal/inventarios").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void crearInventarioDeUnLugar() throws Exception {
        crearInventario("""
                {"eventoId": 42, "sectores": [{"sectorId": 12, "tipo": "NO_NUMERADO", "capacidad": 1}]}""")
                .andExpect(status().isCreated());
    }

    private void crearInventarioDeUnLugarSinHttp() {
        try {
            crearInventarioDeUnLugar();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void agotarUnicoLugar() {
        tx.executeWithoutResult(s ->
                assertThat(inventarioSectorRepository.descontarCupo(EVENTO, CAMPO, 1)).isEqualTo(1));
    }

    private Reserva crearReservaPendiente(long sectorId, int cantidad) {
        return tx.execute(s -> {
            LocalDateTime ahora = reservaRepository.ahora();
            Reserva reserva = new Reserva();
            reserva.setEventoId(EVENTO);
            reserva.setSectorId(sectorId);
            reserva.setClienteId("101");
            reserva.setEstado(EstadoReserva.PENDIENTE);
            reserva.setCantidad(cantidad);
            reserva.setCreadaEn(ahora);
            reserva.setExpiraEn(ahora.plusMinutes(10));
            return reservaRepository.save(reserva);
        });
    }

    private void reservarAsiento(int fila, int numero, UUID reservaId) {
        tx.executeWithoutResult(s ->
                assertThat(asientoRepository.reservar(EVENTO, NUMERADO, fila, numero, reservaId)).isEqualTo(1));
    }

    private void assertNadaCreado() {
        assertThat(contar("SELECT count(*) FROM inventario_evento")).isZero();
        assertThat(contar("SELECT count(*) FROM inventario_sector")).isZero();
        assertThat(contar("SELECT count(*) FROM asiento")).isZero();
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private String estadoReserva(UUID id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    private Boolean agotadoNotificado() {
        return jdbc.queryForObject("SELECT agotado_notificado FROM inventario_evento WHERE evento_id = 42",
                Boolean.class);
    }
}
