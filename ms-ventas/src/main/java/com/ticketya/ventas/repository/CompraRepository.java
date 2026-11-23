package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.enums.EstadoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    @EntityGraph(attributePaths = {"pago", "entradas"})
    Optional<Compra> findByClienteIdAndIdempotencyKey(String clienteId, String idempotencyKey);

    @EntityGraph(attributePaths = {"pago", "entradas"})
    Optional<Compra> findWithDetallesById(UUID id);

    @EntityGraph(attributePaths = {"pago", "entradas"})
    List<Compra> findByEstadoOrderByCreadaEnAsc(EstadoCompra estado);

    Page<Compra> findByClienteIdOrderByCreadaEnDesc(String clienteId, Pageable pageable);
}
