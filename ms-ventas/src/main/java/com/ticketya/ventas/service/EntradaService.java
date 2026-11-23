package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.PageEntradaResponse;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.repository.EntradaRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/** Servicio de consulta y validacion de acceso de entradas emitidas (specs emision-entradas y control-acceso-qr). */
@Service
public class EntradaService {

    private final EntradaRepository entradaRepository;
    private final Clock clock;

    public EntradaService(EntradaRepository entradaRepository) {
        this(entradaRepository, Clock.systemUTC());
    }

    @Autowired
    public EntradaService(EntradaRepository entradaRepository, ObjectProvider<Clock> clockProvider) {
        this(entradaRepository, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    public EntradaService(EntradaRepository entradaRepository, Clock clock) {
        this.entradaRepository = entradaRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageEntradaResponse listarDelCliente(String clienteId, EstadoEntrada estado,
                                                Long eventoId, int page, int size) {
        Page<EntradaResponse> entradas = entradaRepository
                .buscarDelCliente(clienteId, estado, eventoId, PageRequest.of(page, size))
                .map(EntradaResponse::from);
        return PageEntradaResponse.from(entradas);
    }

    @Transactional(readOnly = true)
    public EntradaResponse obtener(UUID id, String solicitanteId, boolean esAdmin) {
        Entrada entrada = entradaRepository.findWithCompraById(id)
                .orElseThrow(() -> new NotFoundException("La entrada " + id + " no existe"));
        if (!esAdmin && !entrada.getCompra().getClienteId().equals(solicitanteId)) {
            throw new ForbiddenException();
        }
        return EntradaResponse.from(entrada);
    }

    /**
     * Valida una entrada en el ingreso por codigo QR sin consultar a ms-eventos.
     * La transicion EMITIDA -> USADA se resuelve atomicamente en la base de datos.
     */
    @Transactional
    public EntradaResponse validar(String codigoQr, Long eventoId, String operadorId) {
        String qrNormalizado = codigoQr.trim();
        LocalDateTime ahora = LocalDateTime.now(clock);
        int actualizadas = entradaRepository.marcarUsadaSiEmitida(qrNormalizado, eventoId, ahora, operadorId);
        if (actualizadas == 1) {
            Entrada entrada = entradaRepository.findByCodigoQr(qrNormalizado).orElseThrow();
            return EntradaResponse.from(entrada);
        }

        Entrada entrada = entradaRepository.findByCodigoQr(qrNormalizado)
                .orElseThrow(() -> new NotFoundException("QR_INEXISTENTE",
                        "No existe una entrada para el codigo QR indicado"));
        if (!entrada.getEventoId().equals(eventoId)) {
            throw new BusinessRuleException("EVENTO_INCORRECTO",
                    "La entrada corresponde a otro evento");
        }
        if (entrada.getEstado() == EstadoEntrada.USADA) {
            throw new ConflictException("ENTRADA_USADA",
                    "La entrada ya fue usada en " + entrada.getUsadaEn());
        }
        throw new ConflictException("ENTRADA_NO_VALIDA",
                "La entrada no es valida para ingresar (estado actual: " + entrada.getEstado() + ")");
    }
}
