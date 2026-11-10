package com.ticketya.ventas.service;

import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.MedioPagoRequest;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.exception.PaymentRequiredException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.enums.EstadoCompra;
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
    void reservaDeOtroClienteResponde403SinCobrarNiRegistrarCompra() {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenThrow(new ForbiddenException());

        assertThrows(ForbiddenException.class,
                () -> compraService.crear("cli-100", "k-6", requestConToken("tok_ok")));

        verifyNoInteractions(pasarelaPagoService);
        assertTrue(db.isEmpty());
    }
}

