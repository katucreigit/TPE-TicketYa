package com.ticketya.ventas.service;

import com.ticketya.ventas.exception.BadRequestException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoCobro;
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
}

