package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Compra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    @EntityGraph(attributePaths = {"pago", "entradas"})
    Optional<Compra> findByClienteIdAndIdempotencyKey(String clienteId, String idempotencyKey);
}
