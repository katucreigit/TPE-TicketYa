package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.repository.EntradaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntradaServiceTest {

    private static final UUID ENTRADA_ID = UUID.fromString("e1b2c3d4-0001-4aaa-8bbb-111122223333");

    private EntradaRepository entradaRepository;
    private EntradaService entradaService;

    @BeforeEach
    void setUp() {
        entradaRepository = mock(EntradaRepository.class);
        entradaService = new EntradaService(entradaRepository);
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
        e.setCodigoQr("9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64");
        e.setEstado(EstadoEntrada.EMITIDA);
        return e;
    }

    @Test
    void obtenerEntradaPropiaDevuelveDetalleConQr() {
        when(entradaRepository.findWithCompraById(ENTRADA_ID)).thenReturn(Optional.of(entradaDe("cli-100")));

        EntradaResponse r = entradaService.obtener(ENTRADA_ID, "cli-100", false);

        assertEquals(ENTRADA_ID, r.entradaId());
        assertEquals("9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64", r.codigoQr());
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
}

