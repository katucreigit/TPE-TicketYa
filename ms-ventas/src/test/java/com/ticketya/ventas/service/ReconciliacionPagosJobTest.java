package com.ticketya.ventas.service;

import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.model.enums.TipoMedioPago;
import com.ticketya.ventas.repository.CompraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReconciliacionPagosJobTest {

    private static final UUID RESERVA_ID_1 = UUID.fromString("5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11");
    private static final UUID RESERVA_ID_2 = UUID.fromString("6b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b22");
    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2027, 1, 15, 21, 0);
    private static final BigDecimal PRECIO_UNITARIO = new BigDecimal("45000.00");
    private static final BigDecimal TOTAL = new BigDecimal("90000.00");

    private final Map<UUID, Compra> db = new ConcurrentHashMap<>();
    private CompraRepository compraRepository;
    private ReservasClient reservasClient;
    private EventosClient eventosClient;
    private PasarelaPagoService pasarelaPagoService;
    private CompraService compraService;
    private ReconciliacionPagosJob job;

    @BeforeEach
    void setUp() {
        db.clear();
        compraRepository = mock(CompraRepository.class);
        reservasClient = mock(ReservasClient.class);
        eventosClient = mock(EventosClient.class);
        pasarelaPagoService = spy(new PasarelaPagoService());

        when(compraRepository.saveAndFlush(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            for (var e : c.getEntradas()) {
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
            }
            db.put(c.getId(), c);
            return c;
        });
        when(compraRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(db.get(inv.getArgument(0))));
        when(compraRepository.findByEstadoOrderByCreadaEnAsc(EstadoCompra.PAGO_INCIERTO)).thenAnswer(inv ->
                db.values().stream()
                        .filter(c -> c.getEstado() == EstadoCompra.PAGO_INCIERTO)
                        .toList());

        TransactionTemplate tx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
        };

        compraService = new CompraService(compraRepository, reservasClient, eventosClient, pasarelaPagoService, tx);
        job = new ReconciliacionPagosJob(compraRepository, compraService, pasarelaPagoService, reservasClient, eventosClient);
    }

    private Compra crearCompraIncierta(UUID compraId, UUID reservaId) {
        Compra c = new Compra();
        c.setId(compraId);
        c.setClienteId("cli-100");
        c.setIdempotencyKey("k-" + compraId);
        c.setReservaId(reservaId);
        c.setEventoId(42L);
        c.setRequestHash("hash-" + compraId);
        c.setTotal(TOTAL);
        c.setEstado(EstadoCompra.PAGO_INCIERTO);

        Pago pago = new Pago();
        pago.setMedioPagoTipo(TipoMedioPago.TARJETA_CREDITO);
        pago.setUltimos4("4242");
        pago.setMonto(TOTAL);
        pago.setEstado(EstadoPago.INCIERTO);
        c.setPagoBidireccional(pago);

        db.put(compraId, c);
        return c;
    }

    private static ReservaRemota reserva(UUID reservaId, String estado) {
        return new ReservaRemota(reservaId, 42L, 11L, "cli-100", estado, 2,
                List.of(new ReservaRemota.Item(3, 10), new ReservaRemota.Item(3, 11)),
                LocalDateTime.now().plusMinutes(10));
    }

    private static SnapshotCompra snapshot() {
        return new SnapshotCompra(42L, 11L, FECHA_EVENTO, PRECIO_UNITARIO);
    }

    @Test
    void reconciliacionResuelveCompraAprobadaConfirmaReservaYEmiteEntradasConQrUnico() {
        UUID compraId = UUID.randomUUID();
        Compra compra = crearCompraIncierta(compraId, RESERVA_ID_1);
        pasarelaPagoService.resolverIncertidumbre(CompraService.claveCobroDe(compraId), EstadoPago.APROBADO);

        when(reservasClient.obtener(RESERVA_ID_1, "cli-100")).thenReturn(reserva(RESERVA_ID_1, "PENDIENTE"));
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshot());

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.APROBADA, compra.getEstado());
        assertEquals(EstadoPago.APROBADO, compra.getPago().getEstado());
        assertNotNull(compra.getPago().getTransaccionPasarela());
        assertEquals(TipoMedioPago.TARJETA_CREDITO, compra.getPago().getMedioPagoTipo());
        assertEquals("4242", compra.getPago().getUltimos4());
        assertEquals(2, compra.getEntradas().size());
        assertEquals(EstadoEntrada.EMITIDA, compra.getEntradas().get(0).getEstado());
        assertNotEquals(compra.getEntradas().get(0).getCodigoQr(), compra.getEntradas().get(1).getCodigoQr());
        verify(reservasClient).confirmar(RESERVA_ID_1);
        verify(pasarelaPagoService, never()).cobrar(anyString(), anyString(), any());
    }

    @Test
    void reconciliacionRecuperaCompraAprobadaSiReservaYaEstabaConfirmadaSinReembolsar() {
        UUID compraId = UUID.randomUUID();
        Compra compra = crearCompraIncierta(compraId, RESERVA_ID_1);
        pasarelaPagoService.resolverIncertidumbre(CompraService.claveCobroDe(compraId), EstadoPago.APROBADO);

        when(reservasClient.obtener(RESERVA_ID_1, "cli-100")).thenReturn(reserva(RESERVA_ID_1, "CONFIRMADA"));
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshot());

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.APROBADA, compra.getEstado());
        assertEquals(EstadoPago.APROBADO, compra.getPago().getEstado());
        assertEquals(2, compra.getEntradas().size());
        verify(reservasClient, never()).confirmar(any());
        verify(pasarelaPagoService, never()).reembolsar(anyString());
    }

    @Test
    void reconciliacionCompensaConReembolsoCuandoReservaVencioYMarcaFallida() {
        UUID compraId = UUID.randomUUID();
        Compra compra = crearCompraIncierta(compraId, RESERVA_ID_1);
        pasarelaPagoService.resolverIncertidumbre(CompraService.claveCobroDe(compraId), EstadoPago.APROBADO);

        when(reservasClient.obtener(RESERVA_ID_1, "cli-100")).thenReturn(reserva(RESERVA_ID_1, "VENCIDA"));

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.FALLIDA, compra.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, compra.getPago().getEstado());
        assertTrue(compra.getEntradas().isEmpty());
        verify(pasarelaPagoService).reembolsar(anyString());
        verify(reservasClient, never()).confirmar(any());
    }

    @Test
    void reconciliacionCompensaConReembolsoSiConfirmarFallaPorReservaVencidaEnVuelo() {
        UUID compraId = UUID.randomUUID();
        Compra compra = crearCompraIncierta(compraId, RESERVA_ID_1);
        pasarelaPagoService.resolverIncertidumbre(CompraService.claveCobroDe(compraId), EstadoPago.APROBADO);

        when(reservasClient.obtener(RESERVA_ID_1, "cli-100")).thenReturn(reserva(RESERVA_ID_1, "PENDIENTE"));
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshot());
        doThrow(new GoneException("Vencio")).when(reservasClient).confirmar(RESERVA_ID_1);

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.FALLIDA, compra.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, compra.getPago().getEstado());
        assertTrue(compra.getEntradas().isEmpty());
        verify(pasarelaPagoService).reembolsar(anyString());
    }

    @Test
    void reconciliacionResuelveCompraRechazadaYLiberaReserva() {
        UUID compraId = UUID.randomUUID();
        Compra compra = crearCompraIncierta(compraId, RESERVA_ID_1);
        pasarelaPagoService.resolverIncertidumbre(CompraService.claveCobroDe(compraId), EstadoPago.RECHAZADO);

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.RECHAZADA, compra.getEstado());
        assertEquals(EstadoPago.RECHAZADO, compra.getPago().getEstado());
        assertTrue(compra.getEntradas().isEmpty());
        verify(reservasClient).liberar(RESERVA_ID_1);
        verify(pasarelaPagoService, never()).reembolsar(anyString());
    }

    @Test
    void reconciliacionResuelveCobroNoConcretadoComoFallidaYLiberaReserva() {
        UUID compraId = UUID.randomUUID();
        Compra compra = crearCompraIncierta(compraId, RESERVA_ID_1);
        // No registrado en pasarela -> consultarEstado devuelve NO_ENCONTRADO

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.FALLIDA, compra.getEstado());
        assertEquals(EstadoPago.FALLIDO, compra.getPago().getEstado());
        assertTrue(compra.getEntradas().isEmpty());
        verify(reservasClient).liberar(RESERVA_ID_1);
    }

    @Test
    void reconciliacionMantienePagoInciertoAnteIncertidumbreOTimeoutYAislaErroresEntreCompras() {
        UUID compraFallidaId = UUID.randomUUID();
        UUID compraExitosaId = UUID.randomUUID();
        Compra compraIncierta = crearCompraIncierta(compraFallidaId, RESERVA_ID_1);
        Compra compraExitosa = crearCompraIncierta(compraExitosaId, RESERVA_ID_2);

        String clave1 = CompraService.claveCobroDe(compraFallidaId);
        String clave2 = CompraService.claveCobroDe(compraExitosaId);
        pasarelaPagoService.programarSimulacionIncertidumbre(clave1, 2, true, EstadoPago.APROBADO);
        assertThrows(GatewayTimeoutException.class,
                () -> pasarelaPagoService.cobrar(clave1, "tok_ok", TOTAL));
        pasarelaPagoService.resolverIncertidumbre(clave2, EstadoPago.APROBADO);

        when(reservasClient.obtener(RESERVA_ID_2, "cli-100")).thenReturn(reserva(RESERVA_ID_2, "PENDIENTE"));
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshot());

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.PAGO_INCIERTO, compraIncierta.getEstado());
        assertEquals(EstadoPago.INCIERTO, compraIncierta.getPago().getEstado());
        assertEquals(EstadoCompra.APROBADA, compraExitosa.getEstado());
        assertEquals(EstadoPago.APROBADO, compraExitosa.getPago().getEstado());
        assertEquals(2, compraExitosa.getEntradas().size());
        verify(reservasClient, never()).liberar(RESERVA_ID_1);
        verify(reservasClient).confirmar(RESERVA_ID_2);

        // Tambien verifica permanencia en PAGO_INCIERTO ante falla temporal 502 de ms-reservas
        UUID compra502Id = UUID.randomUUID();
        Compra compra502 = crearCompraIncierta(compra502Id, RESERVA_ID_1);
        pasarelaPagoService.resolverIncertidumbre(CompraService.claveCobroDe(compra502Id), EstadoPago.APROBADO);
        when(reservasClient.obtener(RESERVA_ID_1, "cli-100")).thenThrow(new BadGatewayException("502 temporal"));

        job.reconciliarPagosInciertos();

        assertEquals(EstadoCompra.PAGO_INCIERTO, compra502.getEstado());
        assertEquals(EstadoPago.INCIERTO, compra502.getPago().getEstado());
    }
}
