package com.ticketya.ventas.service;

import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.client.ReservasClient.DevolucionLugares;
import com.ticketya.ventas.dto.DevolucionRequest;
import com.ticketya.ventas.dto.DevolucionResponse;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Devolucion;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.EstadoDevolucion;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import com.ticketya.ventas.repository.DevolucionRepository;
import com.ticketya.ventas.repository.EntradaRepository;
import com.ticketya.ventas.repository.ReintegroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DevolucionServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** El evento es el 15/01/2027 a las 21:00 hora local (UTC-3). */
    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2027, 1, 15, 21, 0);
    private static final UUID ENTRADA_ID = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
    private static final UUID RESERVA_ID = UUID.fromString("5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11");
    private static final BigDecimal PRECIO = new BigDecimal("45000.00");
    private static final String TX_PAGO = "tx-pago-1";

    private final Map<UUID, Devolucion> devoluciones = new ConcurrentHashMap<>();
    private final Map<UUID, Reintegro> reintegros = new ConcurrentHashMap<>();
    private EntradaRepository entradaRepository;
    private DevolucionRepository devolucionRepository;
    private ReintegroRepository reintegroRepository;
    private ReservasClient reservasClient;
    private PasarelaPagoService pasarela;
    private Entrada entrada;

    @BeforeEach
    void setUp() {
        devoluciones.clear();
        reintegros.clear();
        entradaRepository = mock(EntradaRepository.class);
        devolucionRepository = mock(DevolucionRepository.class);
        reintegroRepository = mock(ReintegroRepository.class);
        reservasClient = mock(ReservasClient.class);
        pasarela = spy(new PasarelaPagoService());

        entrada = entradaDe("cli-100", 3, 10);
        when(entradaRepository.bloquearConCompra(ENTRADA_ID)).thenAnswer(inv -> Optional.of(entrada));
        when(entradaRepository.saveAndFlush(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));

        when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenAnswer(inv -> {
            Devolucion d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            devoluciones.put(d.getId(), d);
            return d;
        });
        when(devolucionRepository.bloquear(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(devoluciones.get(inv.<UUID>getArgument(0))));
        when(devolucionRepository.findDetalleById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(devoluciones.get(inv.<UUID>getArgument(0))));
        when(devolucionRepository.existsByEntradaIdAndEstadoIn(any(UUID.class), any())).thenAnswer(inv -> {
            UUID entradaId = inv.getArgument(0);
            java.util.Collection<?> estados = inv.getArgument(1);
            return devoluciones.values().stream()
                    .anyMatch(d -> d.getEntrada().getId().equals(entradaId) && estados.contains(d.getEstado()));
        });

        when(reintegroRepository.saveAndFlush(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            reintegros.put(r.getId(), r);
            return r;
        });
        when(reintegroRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(reintegros.get(inv.<UUID>getArgument(0))));
        when(reintegroRepository.findByEntradaIdAndTipo(any(UUID.class), any(TipoReintegro.class)))
                .thenAnswer(inv -> reintegros.values().stream()
                        .filter(r -> r.getEntrada().getId().equals(inv.getArgument(0))
                                && r.getTipo() == inv.getArgument(1))
                        .findFirst());
    }

    private DevolucionService servicioEn(LocalDateTime ahoraLocal) {
        Instant instante = ahoraLocal.atZone(ZONA).toInstant();
        Clock clock = Clock.fixed(instante, ZoneOffset.UTC);
        TransactionTemplate tx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
        };
        return new DevolucionService(devolucionRepository, entradaRepository, reintegroRepository,
                reservasClient, pasarela, tx, clock, ZONA, 72);
    }

    private DevolucionService servicioEnPlazo() {
        return servicioEn(FECHA_EVENTO.minusDays(5));
    }

    private static Entrada entradaDe(String clienteId, Integer fila, Integer numero) {
        Compra compra = new Compra();
        compra.setId(UUID.randomUUID());
        compra.setClienteId(clienteId);
        compra.setReservaId(RESERVA_ID);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.APROBADO);
        pago.setMonto(PRECIO);
        pago.setTransaccionPasarela(TX_PAGO);
        compra.setPagoBidireccional(pago);

        Entrada e = new Entrada();
        e.setId(ENTRADA_ID);
        e.setCompra(compra);
        e.setEventoId(42L);
        e.setSectorId(11L);
        e.setFila(fila);
        e.setNumero(numero);
        e.setPrecioUnitario(PRECIO);
        e.setEventoFechaHora(FECHA_EVENTO);
        e.setCodigoQr(UUID.randomUUID().toString());
        e.setEstado(EstadoEntrada.EMITIDA);
        return e;
    }

    private static DevolucionRequest pedido() {
        return new DevolucionRequest(ENTRADA_ID, "No puedo asistir");
    }

    // ------------------------------------------------------------------ solicitar

    @Test
    void solicitarEnPlazoCreaDevolucionSolicitadaYBloqueaLaEntrada() {
        DevolucionResponse r = servicioEnPlazo().solicitar("cli-100", pedido());

        assertEquals(EstadoDevolucion.SOLICITADA, r.estado());
        assertEquals(ENTRADA_ID, r.entradaId());
        assertEquals(0, PRECIO.compareTo(r.monto()));
        assertEquals("No puedo asistir", r.motivo());
        assertNull(r.reintegro());
        assertEquals(EstadoEntrada.DEVOLUCION_SOLICITADA, entrada.getEstado());
    }

    @Test
    void solicitarConExactamente72HorasSePermite() {
        DevolucionResponse r = servicioEn(FECHA_EVENTO.minusHours(72)).solicitar("cli-100", pedido());

        assertEquals(EstadoDevolucion.SOLICITADA, r.estado());
    }

    @Test
    void solicitarUnSegundoDespuesDelLimiteResponde422() {
        DevolucionService servicio = servicioEn(FECHA_EVENTO.minusHours(72).plusSeconds(1));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> servicio.solicitar("cli-100", pedido()));
        assertEquals("FUERA_DE_PLAZO", ex.getCode());
        assertEquals(EstadoEntrada.EMITIDA, entrada.getEstado());
        assertTrue(devoluciones.isEmpty());
    }

    @Test
    void solicitarCon48HorasResponde422() {
        DevolucionService servicio = servicioEn(FECHA_EVENTO.minusHours(48));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> servicio.solicitar("cli-100", pedido()));
        assertEquals(422, ex.getStatus().value());
    }

    @Test
    void elPlazoSeMideEnHoraLocalDelEventoYNoEnUtc() {
        // 73 h antes en hora local; en UTC el reloj marca 3 h mas. Si se comparara en UTC daria fuera de plazo.
        DevolucionResponse r = servicioEn(FECHA_EVENTO.minusHours(73)).solicitar("cli-100", pedido());

        assertEquals(EstadoDevolucion.SOLICITADA, r.estado());
    }

    @Test
    void solicitarDosVecesLaMismaEntradaResponde409() {
        DevolucionService servicio = servicioEnPlazo();
        servicio.solicitar("cli-100", pedido());

        ConflictException ex = assertThrows(ConflictException.class, () -> servicio.solicitar("cli-100", pedido()));
        assertEquals("ENTRADA_NO_DEVOLVIBLE", ex.getCode());
        assertEquals(1, devoluciones.size());
    }

    @Test
    void solicitarConDevolucionEnCursoAunqueLaEntradaFigureEmitidaResponde409() {
        DevolucionService servicio = servicioEnPlazo();
        servicio.solicitar("cli-100", pedido());
        entrada.setEstado(EstadoEntrada.EMITIDA);

        ConflictException ex = assertThrows(ConflictException.class, () -> servicio.solicitar("cli-100", pedido()));
        assertEquals("ENTRADA_NO_DEVOLVIBLE", ex.getCode());
    }

    @Test
    void solicitarEntradaUsadaODevueltaResponde409() {
        DevolucionService servicio = servicioEnPlazo();
        for (EstadoEntrada estado : new EstadoEntrada[]{
                EstadoEntrada.USADA, EstadoEntrada.REINTEGRADA, EstadoEntrada.REINTEGRO_PENDIENTE}) {
            entrada.setEstado(estado);
            assertThrows(ConflictException.class, () -> servicio.solicitar("cli-100", pedido()), estado.name());
        }
        assertTrue(devoluciones.isEmpty());
    }

    @Test
    void solicitarEntradaAjenaResponde403() {
        assertThrows(ForbiddenException.class, () -> servicioEnPlazo().solicitar("cli-200", pedido()));
        assertEquals(EstadoEntrada.EMITIDA, entrada.getEstado());
    }

    @Test
    void solicitarEntradaInexistenteResponde404() {
        DevolucionRequest otra = new DevolucionRequest(UUID.randomUUID(), null);
        when(entradaRepository.bloquearConCompra(otra.entradaId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> servicioEnPlazo().solicitar("cli-100", otra));
    }

    // ------------------------------------------------------------------ obtener

    @Test
    void obtenerDevolucionPropiaOComoAdminYAjena403() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();

        assertEquals(id, servicio.obtener(id, "cli-100", false).devolucionId());
        assertEquals(id, servicio.obtener(id, "admin-1", true).devolucionId());
        assertThrows(ForbiddenException.class, () -> servicio.obtener(id, "cli-200", false));
        assertThrows(NotFoundException.class, () -> servicio.obtener(UUID.randomUUID(), "admin-1", true));
    }

    // ------------------------------------------------------------------ aprobar

    @Test
    void aprobarLiberaElAsientoReintegraYDejaTodoReintegrado() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();

        DevolucionResponse r = servicio.aprobar(id, "admin-1");

        assertEquals(EstadoDevolucion.REINTEGRADA, r.estado());
        assertNotNull(r.reintegro());
        assertEquals(EstadoReintegro.COMPLETADO, r.reintegro().estado());
        assertEquals(0, PRECIO.compareTo(r.reintegro().monto()));
        assertEquals(EstadoEntrada.REINTEGRADA, entrada.getEstado());
        verify(reservasClient).devolver(RESERVA_ID, DevolucionLugares.asiento(3, 10));
        verify(pasarela).reintegrar(anyString(), eq(TX_PAGO), eq(PRECIO));

        Devolucion guardada = devoluciones.get(id);
        assertEquals("admin-1", guardada.getResueltaPor());
        assertTrue(guardada.isLugarLiberado());
        assertEquals(1, reintegros.size());
        assertEquals(TipoReintegro.DEVOLUCION, reintegros.values().iterator().next().getTipo());
    }

    @Test
    void aprobarEntradaDeSectorNoNumeradoDevuelveUnCupo() {
        entrada = entradaDe("cli-100", null, null);
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();

        servicio.aprobar(id, "admin-1");

        verify(reservasClient).devolver(RESERVA_ID, DevolucionLugares.cupo(1));
    }

    @Test
    void siLaPasarelaFallaQuedaEnProcesoYAlReintentarNoSeDevuelveDosVecesNiSeLiberaDosVeces() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();
        doThrow(new BadGatewayException("Pasarela caida"))
                .doCallRealMethod()
                .when(pasarela).reintegrar(anyString(), anyString(), any(BigDecimal.class));

        assertThrows(BadGatewayException.class, () -> servicio.aprobar(id, "admin-1"));
        assertEquals(EstadoDevolucion.EN_PROCESO, devoluciones.get(id).getEstado());
        assertEquals(EstadoEntrada.DEVOLUCION_SOLICITADA, entrada.getEstado());
        assertEquals(EstadoReintegro.PENDIENTE, reintegros.values().iterator().next().getEstado());

        DevolucionResponse r = servicio.aprobar(id, "admin-1");

        assertEquals(EstadoDevolucion.REINTEGRADA, r.estado());
        assertEquals(1, reintegros.size(), "el reintento reutiliza el mismo reintegro");
        verify(reservasClient, times(1)).devolver(any(), any());
        verify(pasarela, times(2)).reintegrar(anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void reintentarConLaMismaClaveNoReintegraDosVecesEnLaPasarela() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();
        // La pasarela reintegra pero la respuesta se pierde (falla despues de registrar el reintegro).
        doAnswer(inv -> {
            inv.callRealMethod();
            throw new BadGatewayException("Respuesta perdida");
        }).doCallRealMethod().when(pasarela).reintegrar(anyString(), anyString(), any(BigDecimal.class));

        assertThrows(BadGatewayException.class, () -> servicio.aprobar(id, "admin-1"));
        servicio.aprobar(id, "admin-1");

        String clave = DevolucionService.claveReintegroDe(reintegros.keySet().iterator().next());
        String primera = pasarela.reintegrar(clave, TX_PAGO, PRECIO).transaccionId();
        String segunda = pasarela.reintegrar(clave, TX_PAGO, PRECIO).transaccionId();
        assertEquals(primera, segunda);
    }

    @Test
    void siFallaLaLiberacionNoSeReintegraYAlReintentarSeHaceTodo() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();
        doThrow(new BadGatewayException("ms-reservas no responde"))
                .doNothing()
                .when(reservasClient).devolver(any(), any());

        assertThrows(BadGatewayException.class, () -> servicio.aprobar(id, "admin-1"));
        assertEquals(EstadoDevolucion.EN_PROCESO, devoluciones.get(id).getEstado());
        assertFalse(devoluciones.get(id).isLugarLiberado());
        verify(pasarela, never()).reintegrar(anyString(), anyString(), any(BigDecimal.class));

        DevolucionResponse r = servicio.aprobar(id, "admin-1");

        assertEquals(EstadoDevolucion.REINTEGRADA, r.estado());
        verify(reservasClient, times(2)).devolver(any(), any());
        verify(pasarela, times(1)).reintegrar(anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void aprobarUnaDevolucionYaReintegradaResponde409SinNuevasLlamadas() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();
        servicio.aprobar(id, "admin-1");

        ConflictException ex = assertThrows(ConflictException.class, () -> servicio.aprobar(id, "admin-1"));
        assertEquals("ESTADO_INVALIDO", ex.getCode());
        verify(reservasClient, times(1)).devolver(any(), any());
        verify(pasarela, times(1)).reintegrar(anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void aprobarDevolucionInexistenteResponde404() {
        assertThrows(NotFoundException.class, () -> servicioEnPlazo().aprobar(UUID.randomUUID(), "admin-1"));
    }

    // ------------------------------------------------------------------ rechazar

    @Test
    void rechazarDejaRechazadaConMotivoYDevuelveLaEntradaAEmitida() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();

        DevolucionResponse r = servicio.rechazar(id, "admin-1", "La entrada ya fue transferida");

        assertEquals(EstadoDevolucion.RECHAZADA, r.estado());
        assertEquals("La entrada ya fue transferida", r.motivoRechazo());
        assertEquals(EstadoEntrada.EMITIDA, entrada.getEstado());
        verifyNoInteractions(reservasClient);
    }

    @Test
    void rechazarUnaDevolucionYaResueltaOEnProcesoResponde409() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();
        servicio.rechazar(id, "admin-1", "No corresponde");

        assertThrows(ConflictException.class, () -> servicio.rechazar(id, "admin-1", "Otra vez"));
        assertThrows(ConflictException.class, () -> servicio.aprobar(id, "admin-1"));

        entrada.setEstado(EstadoEntrada.EMITIDA);
        UUID otra = servicio.solicitar("cli-100", pedido()).devolucionId();
        doThrow(new BadGatewayException("caida")).when(reservasClient).devolver(any(), any());
        assertThrows(BadGatewayException.class, () -> servicio.aprobar(otra, "admin-1"));
        assertThrows(ConflictException.class, () -> servicio.rechazar(otra, "admin-1", "Tarde"));
    }

    @Test
    void trasUnRechazoSePuedeVolverASolicitar() {
        DevolucionService servicio = servicioEnPlazo();
        UUID id = servicio.solicitar("cli-100", pedido()).devolucionId();
        servicio.rechazar(id, "admin-1", "Falta informacion");

        DevolucionResponse nueva = servicio.solicitar("cli-100", pedido());

        assertNotEquals(id, nueva.devolucionId());
        assertEquals(EstadoDevolucion.SOLICITADA, nueva.estado());
    }
}
