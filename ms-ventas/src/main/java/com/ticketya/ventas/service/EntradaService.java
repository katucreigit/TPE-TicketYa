package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.PageEntradaResponse;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.repository.EntradaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Servicio de consulta de entradas emitidas (spec emision-entradas). */
@Service
public class EntradaService {

    private final EntradaRepository entradaRepository;

    public EntradaService(EntradaRepository entradaRepository) {
        this.entradaRepository = entradaRepository;
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
}

