package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.CancelacionEventoResponse;
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
    private CancelacionEventoService servicio;

    @BeforeEach
    void setUp() {
        entradas.clear();
        reintegros.clear();
        entradaRepository = mock(EntradaRepository.class);
        reintegroRepository = mock(ReintegroRepository.class);
        pasarela = spy(new PasarelaPagoService());

        when(entradaRepository.bloquearPorEventoYEstado(anyLong(), any(EstadoEntrada.class))).thenAnswer(inv ->
                entradas.stream()
                        .filter(e -> e.getEventoId().equals(inv.getArgument(0)) && e.getEstado() == inv.getArgument(1))
                        .sorted(Comparator.comparing(Entrada::getId))
                        .toList());
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
        when(reintegroRepository.countByEntradaEventoIdAndTipoAndEstado(anyLong(), any(TipoReintegro.class),
                any(EstadoReintegro.class)))
                .thenAnswer(inv -> (long) reintegrosDe(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)).size());
        when(reintegroRepository.bloquear(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(reintegros.get(inv.<UUID>getArgument(0))));

        TransactionTemplate tx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        servicio = new CancelacionEventoService(entradaRepository, reintegroRepository, pasarela, tx);
    }

    private List<Reintegro> reintegrosDe(Long eventoId, TipoReintegro tipo, EstadoReintegro estado) {
        return reintegros.values().stream()
                .filter(r -> r.getEntrada().getEventoId().equals(eventoId) && r.getTipo() == tipo
                        && r.getEstado() == estado)
                .sorted(Comparator.comparing(Reintegro::getId))
                .toList();
    }

    private Entrada entrada(Long eventoId, EstadoEntrada estado) {
        Compra compra = new Compra();
        compra.setId(UUID.randomUUID());
        compra.setClienteId("cli-100");
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
    void eventoSinVentasRespondeContadoresEnCero() {
        CancelacionEventoResponse respuesta = servicio.procesar(EVENTO_ID);

        assertEquals(new CancelacionEventoResponse(EVENTO_ID, 0, 0, 0), respuesta);
        verifyNoInteractions(pasarela);
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
}
