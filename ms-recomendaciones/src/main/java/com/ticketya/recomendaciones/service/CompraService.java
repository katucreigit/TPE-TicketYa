package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.dto.CompraRegistradaRequest;
import com.ticketya.recomendaciones.model.Compra;
import com.ticketya.recomendaciones.repository.CompraRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Registro idempotente de compras confirmadas (design.md D2): se inserta directo y, si el compraId ya existe, la
 * base lo rechaza por su {@code _id} y el aviso repetido se ignora. Funciona igual con avisos simultaneos.
 */
@Service
public class CompraService {

    private final CompraRepository repository;
    private final Clock clock;

    public CompraService(CompraRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Devuelve true si la compra es nueva y false si ya estaba registrada. */
    public boolean registrar(CompraRegistradaRequest request) {
        Compra compra = new Compra(request.compraId().trim(), request.clienteId().trim(),
                new ArrayList<>(new LinkedHashSet<>(request.eventoIds())),
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        try {
            repository.insertar(compra);
            return true;
        } catch (DuplicateKeyException repetida) {
            return false;
        }
    }
}
