package com.ticketya.ventas.service;

import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.MedioPagoRequest;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.exception.PaymentRequiredException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.model.enums.TipoMedioPago;
import com.ticketya.ventas.repository.CompraRepository;
import com.ticketya.ventas.service.CompraService.ResultadoCompra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompraServiceTest {

    private static final UUID RESERVA_ID = UUID.fromString("5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11");
    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2027, 1, 15, 21, 0);
    private static final BigDecimal PRECIO_UNITARIO = new BigDecimal("45000.00");

    private final Map<UUID, Compra> db = new HashMap<>();
    private CompraRepository compraRepository;
    private ReservasClient reservasClient;
    private EventosClient eventosClient;
    private PasarelaPagoService pasarelaPagoService;
    private CompraService compraService;

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
        when(compraRepository.findByClienteIdAndIdempotencyKey(anyString(), anyString())).thenAnswer(inv -> {
            String cli = inv.getArgument(0);
            String key = inv.getArgument(1);
            return db.values().stream()
                    .filter(c -> cli.equals(c.getClienteId()) && key.equals(c.getIdempotencyKey()))
                    .findFirst();
        });

        TransactionTemplate tx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
        };

        compraService = new CompraService(compraRepository, reservasClient, eventosClient, pasarelaPagoService, tx);
    }

    private static ReservaRemota reservaVigente() {
        return new ReservaRemota(RESERVA_ID, 42L, 11L, "cli-100", "PENDIENTE", 2,
                List.of(new ReservaRemota.Item(3, 10), new ReservaRemota.Item(3, 11)),
                LocalDateTime.now().plusMinutes(10));
    }

    private static SnapshotCompra snapshotEvento() {
        return new SnapshotCompra(42L, 11L, FECHA_EVENTO, PRECIO_UNITARIO);
    }

    private static CompraRequest requestConToken(String token) {
        return new CompraRequest(RESERVA_ID, new MedioPagoRequest(TipoMedioPago.TARJETA_CREDITO, token, "4242"));
    }

    @Test
    void compraExitosaConTokOkConfirmaReservaEmiteEntradasYMarcaAprobada() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        ResultadoCompra resultado = compraService.crear("cli-100", "k-1", requestConToken("tok_ok"));

        assertFalse(resultado.repeticion());
        assertEquals(EstadoCompra.APROBADA, resultado.compra().estado());
        assertEquals(new BigDecimal("90000.00"), resultado.compra().total());
        assertEquals(2, resultado.compra().entradas().size());
        assertNotEquals(resultado.compra().entradas().get(0).codigoQr(),
                resultado.compra().entradas().get(1).codigoQr());
        verify(reservasClient).confirmar(RESERVA_ID);
    }

    @Test
    void pagoRechazadoLiberaLaReservaYMarcaCompraRechazadaCon402() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        assertThrows(PaymentRequiredException.class,
                () -> compraService.crear("cli-100", "k-2", requestConToken("tok_rechazado")));

        verify(reservasClient).liberar(RESERVA_ID);
        verify(reservasClient, never()).confirmar(any());
        Compra guardada = db.values().iterator().next();
        assertEquals(EstadoCompra.RECHAZADA, guardada.getEstado());
        assertEquals(EstadoPago.RECHAZADO, guardada.getPago().getEstado());
    }

    @Test
    void reservaVencidaPrevioAlCobroNoInvocaPasarelaYMarcaFallidaCon410() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenThrow(new GoneException("La reserva vencio"));

        assertThrows(GoneException.class,
                () -> compraService.crear("cli-100", "k-3", requestConToken("tok_ok")));

        verifyNoInteractions(pasarelaPagoService);
        Compra guardada = db.values().iterator().next();
        assertEquals(EstadoCompra.FALLIDA, guardada.getEstado());
    }

    @Test
    void reservaVencidaEnVueloDuranteConfirmacionReembolsaElPagoYMarcaFallidaCon410() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());
        doThrow(new GoneException("La reserva vencio")).when(reservasClient).confirmar(RESERVA_ID);

        assertThrows(GoneException.class,
                () -> compraService.crear("cli-100", "k-4", requestConToken("tok_ok")));

        verify(pasarelaPagoService).reembolsar(anyString());
        Compra guardada = db.values().iterator().next();
        assertEquals(EstadoCompra.FALLIDA, guardada.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, guardada.getPago().getEstado());
    }

    @Test
    void timeoutDePasarelaMantieneCompraPendienteYReintentoReutilizaLaMismaClaveDeCobro() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        assertThrows(GatewayTimeoutException.class,
                () -> compraService.crear("cli-100", "k-5", requestConToken("tok_timeout")));

        Compra pendiente = db.values().iterator().next();
        assertEquals(EstadoCompra.PENDIENTE, pendiente.getEstado());
        verify(reservasClient, never()).liberar(any());

        ResultadoCompra reintento = compraService.crear("cli-100", "k-5", requestConToken("tok_timeout"));

        assertEquals(pendiente.getId(), reintento.compra().compraId());
        assertEquals(EstadoCompra.APROBADA, reintento.compra().estado());
        verify(pasarelaPagoService, times(2)).cobrar(
                eq(CompraService.claveCobroDe(pendiente.getId())), eq("tok_timeout"), eq(new BigDecimal("90000.00")));
    }

    @Test
    void reintentoTrasTimeoutConReservaVencidaPasaDePendienteAFallidaCon410SinNuevoCobro() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        assertThrows(GatewayTimeoutException.class,
                () -> compraService.crear("cli-100", "k-timeout-vencida", requestConToken("tok_timeout")));

        Compra pendiente = db.values().iterator().next();
        assertEquals(EstadoCompra.PENDIENTE, pendiente.getEstado());
        reset(pasarelaPagoService);

        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenThrow(new GoneException("La reserva vencio"));

        assertThrows(GoneException.class,
                () -> compraService.crear("cli-100", "k-timeout-vencida", requestConToken("tok_timeout")));

        assertEquals(EstadoCompra.FALLIDA, db.get(pendiente.getId()).getEstado());
        verifyNoInteractions(pasarelaPagoService);
    }

    @Test
    void falla502MantieneLaCompraPendienteYReintentoReutilizaLaMismaCompraYClaveDeCobro() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());
        doThrow(new BadGatewayException("Pasarela inalcanzable"))
                .doCallRealMethod()
                .when(pasarelaPagoService).cobrar(anyString(), eq("tok_ok"), any(BigDecimal.class));

        assertThrows(BadGatewayException.class,
                () -> compraService.crear("cli-100", "k-502", requestConToken("tok_ok")));

        Compra pendiente = db.values().iterator().next();
        assertEquals(EstadoCompra.PENDIENTE, pendiente.getEstado());
        String claveEsperada = CompraService.claveCobroDe(pendiente.getId());
        assertEquals("cobro-" + pendiente.getId(), claveEsperada);
        verify(reservasClient, never()).liberar(any());

        ResultadoCompra exito = compraService.crear("cli-100", "k-502", requestConToken("tok_ok"));

        assertEquals(pendiente.getId(), exito.compra().compraId());
        assertEquals(EstadoCompra.APROBADA, exito.compra().estado());
        verify(pasarelaPagoService, times(2)).cobrar(eq(claveEsperada), eq("tok_ok"), eq(new BigDecimal("90000.00")));
    }

    @Test
    void emisionDeEntradasEnSectorNumeradoYNoNumeradoCongelaSnapshotYGeneraQrUuidUnico() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        ResultadoCompra numerada = compraService.crear("cli-100", "k-qr-num", requestConToken("tok_ok"));

        Compra compraNumerada = db.get(numerada.compra().compraId());
        assertEquals(2, compraNumerada.getEntradas().size());
        Entrada e1 = compraNumerada.getEntradas().get(0);
        Entrada e2 = compraNumerada.getEntradas().get(1);
        assertEquals(EstadoEntrada.EMITIDA, e1.getEstado());
        assertEquals(EstadoEntrada.EMITIDA, e2.getEstado());
        assertEquals(42L, e1.getEventoId());
        assertEquals(11L, e1.getSectorId());
        assertEquals(3, e1.getFila());
        assertEquals(10, e1.getNumero());
        assertEquals(3, e2.getFila());
        assertEquals(11, e2.getNumero());
        assertEquals(PRECIO_UNITARIO, e1.getPrecioUnitario());
        assertEquals(FECHA_EVENTO, e1.getEventoFechaHora());
        assertDoesNotThrow(() -> UUID.fromString(e1.getCodigoQr()));
        assertDoesNotThrow(() -> UUID.fromString(e2.getCodigoQr()));
        assertNotEquals(e1.getCodigoQr(), e2.getCodigoQr());

        UUID reservaCampoId = UUID.fromString("6b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b22");
        ReservaRemota reservaCampo = new ReservaRemota(
                reservaCampoId, 42L, 12L, "cli-100", "PENDIENTE", 3, List.of(), LocalDateTime.now().plusMinutes(10));
        when(reservasClient.validar(reservaCampoId, "cli-100")).thenReturn(reservaCampo);
        when(eventosClient.obtenerSnapshotParaCompra(42L, 12L))
                .thenReturn(new SnapshotCompra(42L, 12L, FECHA_EVENTO, new BigDecimal("28000.00")));

        CompraRequest reqCampo = new CompraRequest(
                reservaCampoId, new MedioPagoRequest(TipoMedioPago.TARJETA_DEBITO, "tok_ok", "1111"));
        ResultadoCompra campo = compraService.crear("cli-100", "k-qr-campo", reqCampo);

        assertEquals(3, campo.compra().entradas().size());
        assertEquals(3, campo.compra().entradas().stream().map(en -> UUID.fromString(en.codigoQr())).distinct().count());
        campo.compra().entradas().forEach(en -> {
            assertNull(en.fila());
            assertNull(en.numero());
            assertEquals(EstadoEntrada.EMITIDA, en.estado());
            assertEquals(new BigDecimal("28000.00"), en.precioUnitario());
        });
    }

    @Test
    void reservaDeOtroClienteResponde403SinCobrarNiRegistrarCompra() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenThrow(new ForbiddenException());

        assertThrows(ForbiddenException.class,
                () -> compraService.crear("cli-100", "k-6", requestConToken("tok_ok")));

        verifyNoInteractions(pasarelaPagoService);
        assertTrue(db.isEmpty());
    }
}

