package com.ticketya.ventas.service;

import com.ticketya.ventas.exception.BadRequestException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.service.PasarelaPagoService.EstadoConsulta;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoCobro;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoConsultaCobro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PasarelaPagoServiceTest {

    private static final BigDecimal MONTO = new BigDecimal("1500.00");

    private PasarelaPagoService pasarela;

    @BeforeEach
    void setUp() {
        pasarela = new PasarelaPagoService();
    }

    @Test
    void cobroExitosoConTokOkAprueba() {
        ResultadoCobro r = pasarela.cobrar("cobro-1", "tok_ok", MONTO);

        assertEquals(EstadoPago.APROBADO, r.estado());
        assertNotNull(r.transaccionId());
    }

    @Test
    void cobroConTokRechazadoRechaza() {
        ResultadoCobro r = pasarela.cobrar("cobro-1", "tok_rechazado", MONTO);

        assertEquals(EstadoPago.RECHAZADO, r.estado());
        assertNull(r.transaccionId());
    }

    @Test
    void tokTimeoutResponde504LaPrimeraVez() {
        assertThrows(GatewayTimeoutException.class, () -> pasarela.cobrar("cobro-123", "tok_timeout", MONTO));
    }

    @Test
    void tokenNoSoportadoResponde400() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> pasarela.cobrar("cobro-1", "tok_tarjeta_invalida", MONTO));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void montoNoPositivoResponde400() {
        assertThrows(BadRequestException.class, () -> pasarela.cobrar("cobro-1", "tok_ok", BigDecimal.ZERO));
    }

    @Test
    void reintentoDeCobroAprobadoDevuelveElResultadoOriginal() {
        ResultadoCobro primero = pasarela.cobrar("cobro-100", "tok_ok", MONTO);
        ResultadoCobro segundo = pasarela.cobrar("cobro-100", "tok_ok", MONTO);

        assertEquals(primero, segundo);
    }

    @Test
    void reintentoTrasTimeoutDevuelveElCobroRegistradoSinDuplicar() {
        assertThrows(GatewayTimeoutException.class, () -> pasarela.cobrar("cobro-123", "tok_timeout", MONTO));

        ResultadoCobro reintento = pasarela.cobrar("cobro-123", "tok_timeout", MONTO);
        ResultadoCobro otroReintento = pasarela.cobrar("cobro-123", "tok_timeout", MONTO);

        assertEquals(EstadoPago.APROBADO, reintento.estado());
        assertEquals(reintento, otroReintento, "No debe generarse una segunda transaccion");
    }

    @Test
    void reintentoDeCobroRechazadoSigueRechazado() {
        pasarela.cobrar("cobro-9", "tok_rechazado", MONTO);

        assertEquals(EstadoPago.RECHAZADO, pasarela.cobrar("cobro-9", "tok_rechazado", MONTO).estado());
    }

    @Test
    void reembolsoExitosoDeTransaccionExistente() {
        String tx = pasarela.cobrar("cobro-1", "tok_ok", MONTO).transaccionId();

        var r = pasarela.reembolsar(tx);

        assertEquals(tx, r.transaccionId());
        assertEquals(EstadoPago.REEMBOLSADO, r.estado());
    }

    @Test
    void reembolsoRepetidoDevuelveElMismoResultado() {
        String tx = pasarela.cobrar("cobro-1", "tok_ok", MONTO).transaccionId();

        var primero = pasarela.reembolsar(tx);
        var segundo = pasarela.reembolsar(tx);

        assertEquals(primero, segundo);
    }

    @Test
    void reembolsoDeTransaccionInexistenteResponde404() {
        assertThrows(NotFoundException.class, () -> pasarela.reembolsar("tx-inexistente"));
    }

    @Test
    void reintegroRepetidoConLaMismaClaveNoReintegraDosVeces() {
        String tx = pasarela.cobrar("cobro-1", "tok_ok", MONTO).transaccionId();

        var primero = pasarela.reintegrar("reintegro-1", tx, new BigDecimal("750.00"));
        var segundo = pasarela.reintegrar("reintegro-1", tx, new BigDecimal("750.00"));
        var otraEntrada = pasarela.reintegrar("reintegro-2", tx, new BigDecimal("750.00"));

        assertEquals(EstadoPago.REEMBOLSADO, primero.estado());
        assertEquals(primero, segundo);
        assertNotEquals(primero.transaccionId(), otraEntrada.transaccionId());
    }

    @Test
    void reintegroSinTransaccionOMontoInvalidoResponde400() {
        assertThrows(BadRequestException.class, () -> pasarela.reintegrar("reintegro-1", null, MONTO));
        assertThrows(BadRequestException.class, () -> pasarela.reintegrar("reintegro-1", "tx-1", BigDecimal.ZERO));
        assertThrows(BadRequestException.class, () -> pasarela.reintegrar(" ", "tx-1", MONTO));
    }

    @Test
    void cobrosConClavesDistintasGeneranTransaccionesDistintasYSinDobleDebito() {
        ResultadoCobro c1 = pasarela.cobrar("cobro-a", "tok_ok", MONTO);
        ResultadoCobro c1Repetido = pasarela.cobrar("cobro-a", "tok_ok", MONTO);
        ResultadoCobro c2 = pasarela.cobrar("cobro-b", "tok_ok", MONTO);

        assertEquals(c1, c1Repetido);
        assertNotEquals(c1.transaccionId(), c2.transaccionId());
    }

    @Test
    void consultarEstadoDevuelveAprobadoRechazadoYNoEncontrado() {
        ResultadoCobro aprobado = pasarela.cobrar("cobro-ap", "tok_ok", MONTO);
        pasarela.cobrar("cobro-rej", "tok_rechazado", MONTO);

        ResultadoConsultaCobro resAprobado = pasarela.consultarEstado("cobro-ap");
        assertTrue(resAprobado.aprobado());
        assertTrue(resAprobado.esDefinitivo());
        assertEquals(EstadoConsulta.APROBADO, resAprobado.estado());
        assertEquals(aprobado.transaccionId(), resAprobado.transaccionId());

        ResultadoConsultaCobro resRechazado = pasarela.consultarEstado("cobro-rej");
        assertTrue(resRechazado.rechazado());
        assertTrue(resRechazado.esDefinitivo());
        assertEquals(EstadoConsulta.RECHAZADO, resRechazado.estado());
        assertNull(resRechazado.transaccionId());

        ResultadoConsultaCobro resInexistente = pasarela.consultarEstado("cobro-inexistente");
        assertTrue(resInexistente.noEncontrado());
        assertFalse(resInexistente.esDefinitivo());
        assertEquals(EstadoConsulta.NO_ENCONTRADO, resInexistente.estado());
        assertNull(resInexistente.transaccionId());
    }

    @Test
    void consultarEstadoConClaveVaciaONulaResponde400() {
        assertThrows(BadRequestException.class, () -> pasarela.consultarEstado(null));
        assertThrows(BadRequestException.class, () -> pasarela.consultarEstado("  "));
    }

    @Test
    void simulacionTokTimeoutProduceIncertidumbreInicialYLuegoResuelveAprobado() {
        assertThrows(GatewayTimeoutException.class, () -> pasarela.cobrar("cobro-t1", "tok_timeout", MONTO));

        ResultadoConsultaCobro c1 = pasarela.consultarEstado("cobro-t1");
        ResultadoConsultaCobro c2 = pasarela.consultarEstado("cobro-t1");
        ResultadoConsultaCobro c3 = pasarela.consultarEstado("cobro-t1");

        assertTrue(c1.incierto());
        assertTrue(c2.incierto());
        assertTrue(c3.aprobado());
        assertNotNull(c3.transaccionId());
        assertEquals(c3, pasarela.consultarEstado("cobro-t1"));
    }

    @Test
    void simulacionTokTimeoutRechazadoProduceIncertidumbreYLuegoResuelveRechazado() {
        assertThrows(GatewayTimeoutException.class,
                () -> pasarela.cobrar("cobro-t2", "tok_timeout_rechazado", MONTO));

        assertEquals(EstadoConsulta.INCIERTO, pasarela.consultarEstado("cobro-t2").estado());
        assertEquals(EstadoConsulta.INCIERTO, pasarela.consultarEstado("cobro-t2").estado());
        ResultadoConsultaCobro finalRes = pasarela.consultarEstado("cobro-t2");
        assertTrue(finalRes.rechazado());
        assertNull(finalRes.transaccionId());
    }

    @Test
    void simulacionTokTimeoutNoConcretadoProduceIncertidumbreYLuegoResuelveNoEncontrado() {
        assertThrows(GatewayTimeoutException.class,
                () -> pasarela.cobrar("cobro-t3", "tok_timeout_no_concretado", MONTO));

        assertEquals(EstadoConsulta.INCIERTO, pasarela.consultarEstado("cobro-t3").estado());
        assertEquals(EstadoConsulta.INCIERTO, pasarela.consultarEstado("cobro-t3").estado());
        assertEquals(EstadoConsulta.NO_ENCONTRADO, pasarela.consultarEstado("cobro-t3").estado());
    }

    @Test
    void simulacionProgramaticaControlaTimeoutsEnConsultaYResolucionPosterior() {
        pasarela.programarSimulacionIncertidumbre("cobro-prog-1", 1, true, EstadoPago.APROBADO);

        assertThrows(GatewayTimeoutException.class, () -> pasarela.cobrar("cobro-prog-1", "tok_ok", MONTO));
        assertThrows(GatewayTimeoutException.class, () -> pasarela.consultarEstado("cobro-prog-1"));
        ResultadoConsultaCobro resuelto = pasarela.consultarEstado("cobro-prog-1");
        assertTrue(resuelto.aprobado());
        assertNotNull(resuelto.transaccionId());

        pasarela.programarSimulacionTimeoutNoRegistrado("cobro-prog-2", 1, 0);
        assertThrows(GatewayTimeoutException.class, () -> pasarela.cobrar("cobro-prog-2", "tok_ok", MONTO));
        assertTrue(pasarela.consultarEstado("cobro-prog-2").noEncontrado());
        ResultadoCobro reintento = pasarela.cobrar("cobro-prog-2", "tok_ok", MONTO);
        assertTrue(reintento.aprobado());
        assertEquals(reintento.transaccionId(), pasarela.consultarEstado("cobro-prog-2").transaccionId());

        pasarela.programarSimulacionIncertidumbre("cobro-prog-3", 5, false, EstadoPago.RECHAZADO);
        assertThrows(GatewayTimeoutException.class, () -> pasarela.cobrar("cobro-prog-3", "tok_ok", MONTO));
        assertTrue(pasarela.consultarEstado("cobro-prog-3").incierto());
        pasarela.resolverIncertidumbre("cobro-prog-3", EstadoPago.RECHAZADO);
        assertTrue(pasarela.consultarEstado("cobro-prog-3").rechazado());
    }
}

