package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.repository.EntradaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntradaServiceTest {

    private static final UUID ENTRADA_ID = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");
    private static final String QR = "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 23, 20, 0, 0);
    private static final Clock CLOCK = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private EntradaRepository entradaRepository;
    private EntradaService entradaService;

    @BeforeEach
    void setUp() {
        entradaRepository = mock(EntradaRepository.class);
        entradaService = new EntradaService(entradaRepository, CLOCK);
    }

    private static Entrada entradaDe(String clienteId) {
        Compra compra = new Compra();
        compra.setClienteId(clienteId);

        Entrada e = new Entrada();
        e.setId(ENTRADA_ID);
        e.setCompra(compra);
        e.setEventoId(42L);
        e.setSectorId(11L);
        e.setFila(3);
        e.setNumero(10);
        e.setPrecioUnitario(new BigDecimal("45000.00"));
        e.setEventoFechaHora(LocalDateTime.of(2027, 1, 15, 21, 0));
        e.setCodigoQr(QR);
        e.setEstado(EstadoEntrada.EMITIDA);
        return e;
    }

    @Test
    void obtenerEntradaPropiaDevuelveDetalleConQr() {
        when(entradaRepository.findWithCompraById(ENTRADA_ID)).thenReturn(Optional.of(entradaDe("cli-100")));

        EntradaResponse r = entradaService.obtener(ENTRADA_ID, "cli-100", false);

        assertEquals(ENTRADA_ID, r.entradaId());
        assertEquals(QR, r.codigoQr());
    }

    @Test
    void obtenerEntradaDeOtroClienteLanza403Forbidden() {
        when(entradaRepository.findWithCompraById(ENTRADA_ID)).thenReturn(Optional.of(entradaDe("cli-200")));

        assertThrows(ForbiddenException.class, () -> entradaService.obtener(ENTRADA_ID, "cli-100", false));
    }

    @Test
    void adminPuedeObtenerEntradaDeCualquierCliente() {
        when(entradaRepository.findWithCompraById(ENTRADA_ID)).thenReturn(Optional.of(entradaDe("cli-200")));

        EntradaResponse r = entradaService.obtener(ENTRADA_ID, "admin-1", true);

        assertEquals(ENTRADA_ID, r.entradaId());
    }

    @Test
    void obtenerEntradaInexistenteLanza404NotFound() {
        when(entradaRepository.findWithCompraById(ENTRADA_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> entradaService.obtener(ENTRADA_ID, "cli-100", false));
    }

    @Test
    void validarEntradaEmitidaLaMarcaUsadaConFechaYOperador() {
        Entrada usada = entradaDe("cli-100");
        usada.setEstado(EstadoEntrada.USADA);
        usada.setUsadaEn(AHORA);
        usada.setValidadaPor("acc-7");
        when(entradaRepository.marcarUsadaSiEmitida(QR, 42L, AHORA, "acc-7")).thenReturn(1);
        when(entradaRepository.findByCodigoQr(QR)).thenReturn(Optional.of(usada));

        EntradaResponse r = entradaService.validar("  " + QR + "  ", 42L, "acc-7");

        assertEquals(EstadoEntrada.USADA, r.estado());
        assertEquals(AHORA, r.usadaEn());
        assertEquals("acc-7", r.validadaPor());
        verify(entradaRepository).marcarUsadaSiEmitida(QR, 42L, AHORA, "acc-7");
    }

    @Test
    void validarQrInexistenteLanza404QrInexistente() {
        when(entradaRepository.marcarUsadaSiEmitida(QR, 42L, AHORA, "acc-7")).thenReturn(0);
        when(entradaRepository.findByCodigoQr(QR)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> entradaService.validar(QR, 42L, "acc-7"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("QR_INEXISTENTE", ex.getCode());
    }

    @Test
    void validarEventoIncorrectoLanza422AntesDeEvaluarEstado() {
        Entrada deOtroEvento = entradaDe("cli-100");
        deOtroEvento.setEstado(EstadoEntrada.USADA);
        when(entradaRepository.marcarUsadaSiEmitida(QR, 99L, AHORA, "acc-7")).thenReturn(0);
        when(entradaRepository.findByCodigoQr(QR)).thenReturn(Optional.of(deOtroEvento));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> entradaService.validar(QR, 99L, "acc-7"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("EVENTO_INCORRECTO", ex.getCode());
    }

    @Test
    void validarEntradaYaUsadaLanza409EntradaUsadaConMarcaTemporal() {
        LocalDateTime usoPrevio = LocalDateTime.of(2026, 11, 23, 19, 15, 30);
        Entrada usada = entradaDe("cli-100");
        usada.setEstado(EstadoEntrada.USADA);
        usada.setUsadaEn(usoPrevio);
        when(entradaRepository.marcarUsadaSiEmitida(QR, 42L, AHORA, "acc-7")).thenReturn(0);
        when(entradaRepository.findByCodigoQr(QR)).thenReturn(Optional.of(usada));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> entradaService.validar(QR, 42L, "acc-7"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ENTRADA_USADA", ex.getCode());
        assertTrue(ex.getMessage().contains(usoPrevio.toString()));
    }

    @Test
    void validarEntradaEnDevolucionOReintegradaLanza409EntradaNoValidaConEstado() {
        when(entradaRepository.marcarUsadaSiEmitida(QR, 42L, AHORA, "acc-7")).thenReturn(0);
        for (EstadoEntrada estado : new EstadoEntrada[]{
                EstadoEntrada.DEVOLUCION_SOLICITADA,
                EstadoEntrada.REINTEGRO_PENDIENTE,
                EstadoEntrada.REINTEGRADA
        }) {
            Entrada entrada = entradaDe("cli-100");
            entrada.setEstado(estado);
            when(entradaRepository.findByCodigoQr(QR)).thenReturn(Optional.of(entrada));

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> entradaService.validar(QR, 42L, "acc-7"), estado.name());

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("ENTRADA_NO_VALIDA", ex.getCode());
            assertTrue(ex.getMessage().contains(estado.name()));
        }
    }
}
