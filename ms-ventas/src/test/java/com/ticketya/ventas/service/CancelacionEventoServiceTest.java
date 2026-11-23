package com.ticketya.ventas.service;

import com.ticketya.ventas.client.NotificacionesClient;
import com.ticketya.ventas.client.NotificacionesClient.TipoNotificacion;
import com.ticketya.ventas.dto.CancelacionEventoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import com.ticketya.ventas.repository.EntradaRepository;
import com.ticketya.ventas.repository.ReintegroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Spec cancelacion-evento-reintegros: reintegro masivo, idempotencia y fallas parciales.
 * Los repositorios se simulan con mapas en memoria; la pasarela es el mock real (espiado).
 */
class CancelacionEventoServiceTest {

    private static final Long EVENTO_ID = 42L;
    private static final Long OTRO_EVENTO_ID = 7L;
    private static final BigDecimal PRECIO = new BigDecimal("45000.00");

    private final List<Entrada> entradas = new ArrayList<>();
    private final Map<UUID, Reintegro> reintegros = new ConcurrentHashMap<>();
    private EntradaRepository entradaRepository;
    private ReintegroRepository reintegroRepository;
    private PasarelaPagoService pasarela;
    private NotificacionesClient notificacionesClient;
    private TransactionTemplate tx;
    private CancelacionEventoService servicio;

    @BeforeEach
    void setUp() {
        entradas.clear();
        reintegros.clear();
        entradaRepository = mock(EntradaRepository.class);
        reintegroRepository = mock(ReintegroRepository.class);
        pasarela = spy(new PasarelaPagoService());
        notificacionesClient = mock(NotificacionesClient.class);

        when(entradaRepository.findByEventoIdAndEstadoOrderById(anyLong(), any(EstadoEntrada.class))).thenAnswer(inv ->
                entradas.stream()
                        .filter(e -> e.getEventoId().equals(inv.getArgument(0)) && e.getEstado() == inv.getArgument(1))
                        .sorted(Comparator.comparing(Entrada::getId))
                        .toList());
        when(entradaRepository.findById(any(UUID.class))).thenAnswer(inv ->
                entradas.stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
        when(entradaRepository.transicionarDesdeEmitida(any(UUID.class), any(EstadoEntrada.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            EstadoEntrada nuevo = inv.getArgument(1);
            Entrada e = entradas.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (e != null && e.getEstado() == EstadoEntrada.EMITIDA) {
                e.setEstado(nuevo);
                return 1;
            }
            return 0;
        });
        when(entradaRepository.saveAndFlush(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));

        when(reintegroRepository.saveAndFlush(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) {
                // Simula UNIQUE(entrada_id, tipo).
                boolean duplicado = reintegros.values().stream().anyMatch(o ->
                        o.getEntrada().getId().equals(r.getEntrada().getId()) && o.getTipo() == r.getTipo());
                if (duplicado) {
                    throw new org.springframework.dao.DataIntegrityViolationException("uk_reintegro_entrada_tipo");
                }
                r.setId(UUID.randomUUID());
            }
            reintegros.put(r.getId(), r);
            return r;
        });
        when(reintegroRepository.findByEntradaIdAndTipo(any(UUID.class), any(TipoReintegro.class)))
                .thenAnswer(inv -> reintegros.values().stream()
                        .filter(r -> r.getEntrada().getId().equals(inv.getArgument(0))
                                && r.getTipo() == inv.getArgument(1))
                        .findFirst());
        when(reintegroRepository.buscarPorEvento(anyLong(), any(TipoReintegro.class), any(EstadoReintegro.class)))
                .thenAnswer(inv -> reintegrosDe(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(reintegroRepository.buscarTodosPorEvento(anyLong(), any(TipoReintegro.class)))
                .thenAnswer(inv -> reintegros.values().stream()
                        .filter(r -> r.getEntrada().getEventoId().equals(inv.getArgument(0))
                                && r.getTipo() == inv.getArgument(1))
                        .sorted(Comparator.comparing((Reintegro r) -> r.getEntrada().getCompra().getClienteId())
                                .thenComparing(r -> r.getEntrada().getCompra().getId())
                                .thenComparing(Reintegro::getId))
                        .toList());
        when(reintegroRepository.countByEntradaEventoIdAndTipoAndEstado(anyLong(), any(TipoReintegro.class),
                any(EstadoReintegro.class)))
                .thenAnswer(inv -> (long) reintegrosDe(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)).size());
        when(reintegroRepository.bloquear(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(reintegros.get(inv.<UUID>getArgument(0))));

        tx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        servicio = new CancelacionEventoService(entradaRepository, reintegroRepository, pasarela,
                notificacionesClient, tx);
    }

    private List<Reintegro> reintegrosDe(Long eventoId, TipoReintegro tipo, EstadoReintegro estado) {
        return reintegros.values().stream()
                .filter(r -> r.getEntrada().getEventoId().equals(eventoId) && r.getTipo() == tipo
                        && r.getEstado() == estado)
                .sorted(Comparator.comparing(Reintegro::getId))
                .toList();
    }

    private Entrada entrada(Long eventoId, EstadoEntrada estado) {
        return entradaParaCliente(eventoId, estado, "cli-100", null);
    }

    private Entrada entradaParaCliente(Long eventoId, EstadoEntrada estado, String clienteId, String clienteEmail) {
        Compra compra = new Compra();
        compra.setId(UUID.randomUUID());
        compra.setClienteId(clienteId);
        compra.setClienteEmail(clienteEmail);
        compra.setEventoId(eventoId);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.APROBADO);
        pago.setMonto(PRECIO);
        pago.setTransaccionPasarela("tx-" + UUID.randomUUID());
        compra.setPagoBidireccional(pago);

        Entrada e = new Entrada();
        e.setId(UUID.randomUUID());
        e.setEventoId(eventoId);
        e.setSectorId(1L);
        e.setPrecioUnitario(PRECIO);
        e.setEventoFechaHora(LocalDateTime.of(2027, 1, 15, 21, 0));
        e.setCodigoQr(UUID.randomUUID().toString());
        e.setEstado(estado);
        compra.agregarEntrada(e);
        entradas.add(e);
        return e;
    }

    private List<Entrada> emitidas(int cantidad) {
        List<Entrada> lista = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            lista.add(entrada(EVENTO_ID, EstadoEntrada.EMITIDA));
        }
        return lista;
    }

    private String claveDe(Entrada entrada) {
        Reintegro r = reintegros.values().stream()
                .filter(x -> x.getEntrada() == entrada && x.getTipo() == TipoReintegro.CANCELACION_EVENTO)
                .findFirst().orElseThrow();
        return DevolucionService.claveReintegroDe(r.getId());
    }

    // ------------------------------------------------------------------ reintegro masivo

    @Test
    void reintegraTodasLasEntradasEmitidasDelEvento() {
        List<Entrada> vendidas = emitidas(50);

        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 50, 50, 0), respuesta);
        assertTrue(vendidas.stream().allMatch(e -> e.getEstado() == EstadoEntrada.REINTEGRADA));
        assertEquals(50, reintegros.size());
        for (Reintegro r : reintegros.values()) {
            assertEquals(TipoReintegro.CANCELACION_EVENTO, r.getTipo());
            assertEquals(EstadoReintegro.COMPLETADO, r.getEstado());
            assertEquals(PRECIO, r.getMonto());
            assertNull(r.getDevolucion());
            assertNotNull(r.getTransaccionPasarela());
        }
        verify(pasarela, times(50)).reintegrar(anyString(), anyString(), eq(PRECIO));
    }

    @Test
    void reintegraContraLaTransaccionDelPagoDeCadaCompra() {
        Entrada e = emitidas(1).get(0);

        servicio.procesar(EVENTO_ID);

        verify(pasarela).reintegrar(claveDe(e), e.getCompra().getPago().getTransaccionPasarela(), PRECIO);
    }

    @Test
    void soloAfectaEntradasEmitidasDelEventoCancelado() {
        Entrada emitida = entrada(EVENTO_ID, EstadoEntrada.EMITIDA);
        Entrada usada = entrada(EVENTO_ID, EstadoEntrada.USADA);
        Entrada yaDevuelta = entrada(EVENTO_ID, EstadoEntrada.REINTEGRADA);
        Entrada deOtroEvento = entrada(OTRO_EVENTO_ID, EstadoEntrada.EMITIDA);

        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 1, 1, 0), respuesta);
        assertEquals(EstadoEntrada.REINTEGRADA, emitida.getEstado());
        assertEquals(EstadoEntrada.USADA, usada.getEstado());
        assertEquals(EstadoEntrada.REINTEGRADA, yaDevuelta.getEstado());
        assertEquals(EstadoEntrada.EMITIDA, deOtroEvento.getEstado());
        assertEquals(1, reintegros.size());
    }

    @Test
    void omiteEntradaQueDejoDeEstarEmitidaAntesDelUpdateCondicional() {
        Entrada ganadora = entrada(EVENTO_ID, EstadoEntrada.EMITIDA);
        Entrada usadaEnSimultaneo = entrada(EVENTO_ID, EstadoEntrada.EMITIDA);
        doAnswer(inv -> {
            usadaEnSimultaneo.setEstado(EstadoEntrada.USADA);
            return 0;
        }).when(entradaRepository).transicionarDesdeEmitida(
                usadaEnSimultaneo.getId(), EstadoEntrada.REINTEGRO_PENDIENTE);

        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 1, 1, 0), respuesta);
        assertEquals(EstadoEntrada.REINTEGRADA, ganadora.getEstado());
        assertEquals(EstadoEntrada.USADA, usadaEnSimultaneo.getEstado());
        assertEquals(1, reintegros.size());
        assertTrue(reintegros.values().stream().noneMatch(r -> r.getEntrada().getId().equals(usadaEnSimultaneo.getId())));
    }

    @Test
    void eventoSinVentasRespondeContadoresEnCero() {
        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 0, 0, 0), respuesta);
        verifyNoInteractions(pasarela);
        verifyNoInteractions(notificacionesClient);
    }

    // ------------------------------------------------------------------ idempotencia

    @Test
    void avisoRepetidoNoReintegraDosVeces() {
        List<Entrada> vendidas = emitidas(3);

        CancelacionEventoResponse primera = servicio.procesar(EVENTO_ID);
        CancelacionEventoResponse segunda = servicio.procesar(EVENTO_ID);

        assertEquals(primera, segunda);
        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 3, 3, 0), segunda);
        assertEquals(3, reintegros.size());
        verify(pasarela, times(3)).reintegrar(anyString(), anyString(), any());
        assertTrue(vendidas.stream().allMatch(e -> e.getEstado() == EstadoEntrada.REINTEGRADA));
    }

    @Test
    void reintegroPendienteExistenteSeReutilizaSinCrearOtro() {
        // Entrada que quedo EMITIDA con su fila de reintegro ya creada (por ejemplo, un rollback parcial).
        Entrada e = emitidas(1).get(0);
        Reintegro previo = new Reintegro();
        previo.setEntrada(e);
        previo.setTipo(TipoReintegro.CANCELACION_EVENTO);
        previo.setMonto(PRECIO);
        previo.setEstado(EstadoReintegro.PENDIENTE);
        reintegroRepository.saveAndFlush(previo);

        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 1, 1, 0), respuesta);
        assertEquals(1, reintegros.size());
        assertEquals(EstadoReintegro.COMPLETADO, previo.getEstado());
    }

    // ------------------------------------------------------------------ fallas parciales

    @Test
    void fallaParcialDejaLaEntradaPendienteYContinuaConLasDemas() {
        List<Entrada> vendidas = emitidas(50);
        Entrada fallida = vendidas.get(17);
        // La clave depende del id del reintegro, que se crea al procesar: se falla por transaccion de pago.
        String txFallida = fallida.getCompra().getPago().getTransaccionPasarela();
        doThrow(new BadGatewayException("pasarela caida"))
                .when(pasarela).reintegrar(anyString(), eq(txFallida), any());

        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 50, 49, 1), respuesta);
        assertEquals(EstadoEntrada.REINTEGRO_PENDIENTE, fallida.getEstado());
        assertEquals(49, vendidas.stream().filter(e -> e.getEstado() == EstadoEntrada.REINTEGRADA).count());
        Reintegro pendiente = reintegros.values().stream()
                .filter(r -> r.getEntrada() == fallida).findFirst().orElseThrow();
        assertEquals(EstadoReintegro.PENDIENTE, pendiente.getEstado());
        assertNull(pendiente.getTransaccionPasarela());
    }

    @Test
    void repetirLaCancelacionReintentaSoloLoQueFallo() {
        List<Entrada> vendidas = emitidas(5);
        Entrada fallida = vendidas.get(2);
        String txFallida = fallida.getCompra().getPago().getTransaccionPasarela();
        doThrow(new BadGatewayException("pasarela caida"))
                .doCallRealMethod()
                .when(pasarela).reintegrar(anyString(), eq(txFallida), any());

        CancelacionEventoResponse primera = servicio.procesar(EVENTO_ID);
        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 5, 4, 1), primera);

        clearInvocations(pasarela);
        CancelacionEventoResponse segunda = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 5, 5, 0), segunda);
        assertEquals(EstadoEntrada.REINTEGRADA, fallida.getEstado());
        // En el reintento solo se llama a la pasarela por la entrada que habia fallado, con la misma clave.
        verify(pasarela, times(1)).reintegrar(anyString(), anyString(), any());
        verify(pasarela).reintegrar(claveDe(fallida), txFallida, PRECIO);
        assertEquals(5, reintegros.size());
    }

    @Test
    void fallaAlRegistrarElResultadoNoDuplicaElReintegroAlReintentar() {
        Entrada e = emitidas(1).get(0);
        // La pasarela reintegra pero falla el bloqueo para registrar el resultado (por ejemplo, la base no responde).
        doThrow(new org.springframework.dao.QueryTimeoutException("timeout"))
                .doAnswer(inv -> Optional.ofNullable(reintegros.get(inv.<UUID>getArgument(0))))
                .when(reintegroRepository).bloquear(any(UUID.class));

        CancelacionEventoResponse primera = servicio.procesar(EVENTO_ID);
        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 1, 0, 1), primera);
        assertEquals(EstadoEntrada.REINTEGRO_PENDIENTE, e.getEstado());
        String transaccionReintegro = pasarela.reintegrar(claveDe(e), "x", PRECIO).transaccionId();

        CancelacionEventoResponse segunda = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 1, 1, 0), segunda);
        Reintegro r = reintegros.values().iterator().next();
        // La pasarela devolvio el resultado almacenado por clave: un solo reintegro real.
        assertEquals(transaccionReintegro, r.getTransaccionPasarela());
        assertEquals(EstadoEntrada.REINTEGRADA, e.getEstado());
    }

    @Test
    void entradaSinTransaccionDePagoQuedaPendiente() {
        Entrada e = emitidas(1).get(0);
        e.getCompra().getPago().setTransaccionPasarela(null);

        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 1, 0, 1), respuesta);
        assertEquals(EstadoEntrada.REINTEGRO_PENDIENTE, e.getEstado());
    }

    @Test
    void notificaEventoCanceladoAgrupadoPorClienteYUsaMismaClaveAlRepetir() {
        // cli-1 tiene dos compras distintas en el mismo evento (2 entradas en total)
        entradaParaCliente(EVENTO_ID, EstadoEntrada.EMITIDA, "cli-1", "uno@example.com");
        entradaParaCliente(EVENTO_ID, EstadoEntrada.EMITIDA, "cli-1", null);
        // cli-2 tiene una compra sin email guardado
        entradaParaCliente(EVENTO_ID, EstadoEntrada.EMITIDA, "cli-2", null);

        CancelacionEventoResponse primera = servicio.procesar(EVENTO_ID);
        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 3, 3, 0), primera);

        verify(notificacionesClient).enviar(
                eq(TipoNotificacion.EVENTO_CANCELADO),
                eq("cli-1"),
                eq("uno@example.com"),
                argThat(datos -> EVENTO_ID.equals(datos.get("eventoId"))
                        && ((List<?>) datos.get("comprasIds")).size() == 2
                        && Long.valueOf(2L).equals(datos.get("entradasAfectadas"))
                        && Long.valueOf(2L).equals(datos.get("reintegrosCompletados"))
                        && Long.valueOf(0L).equals(datos.get("reintegrosPendientes"))
                        && new BigDecimal("90000.00").equals(datos.get("montoReintegrado"))),
                eq("evento-cancelado-" + EVENTO_ID + "-cli-1")
        );
        verify(notificacionesClient).enviar(
                eq(TipoNotificacion.EVENTO_CANCELADO),
                eq("cli-2"),
                isNull(),
                argThat(datos -> Long.valueOf(1L).equals(datos.get("entradasAfectadas"))
                        && new BigDecimal("45000.00").equals(datos.get("montoReintegrado"))),
                eq("evento-cancelado-" + EVENTO_ID + "-cli-2")
        );
        verifyNoMoreInteractions(notificacionesClient);

        // Repeticion de cancelacion vuelve a emitir con la misma clave determinista para deduplicacion
        clearInvocations(notificacionesClient);
        servicio.procesar(EVENTO_ID);

        verify(notificacionesClient).enviar(eq(TipoNotificacion.EVENTO_CANCELADO), eq("cli-1"),
                eq("uno@example.com"), anyMap(), eq("evento-cancelado-" + EVENTO_ID + "-cli-1"));
        verify(notificacionesClient).enviar(eq(TipoNotificacion.EVENTO_CANCELADO), eq("cli-2"),
                isNull(), anyMap(), eq("evento-cancelado-" + EVENTO_ID + "-cli-2"));
    }

    @Test
    void falloAlNotificarUnCompradorNoImpideNotificarAlSiguienteNiAfectaReintegros() {
        entradaParaCliente(EVENTO_ID, EstadoEntrada.EMITIDA, "cli-1", "uno@example.com");
        entradaParaCliente(EVENTO_ID, EstadoEntrada.EMITIDA, "cli-2", "dos@example.com");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ms-notificaciones");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "http://ms-notificaciones/internal/notificaciones"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(
                        "$.destinatario.usuarioId").value("cli-1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "http://ms-notificaciones/internal/notificaciones"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(
                        "$.destinatario.usuarioId").value("cli-2"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        CancelacionEventoService servicioConClienteReal = new CancelacionEventoService(
                entradaRepository, reintegroRepository, pasarela,
                new NotificacionesClient(builder.build(), "ticketya.local"), tx);

        CancelacionEventoResponse respuesta = servicioConClienteReal.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 2, 2, 0), respuesta);
        assertTrue(entradas.stream().allMatch(e -> e.getEstado() == EstadoEntrada.REINTEGRADA));
        server.verify();
    }
}
