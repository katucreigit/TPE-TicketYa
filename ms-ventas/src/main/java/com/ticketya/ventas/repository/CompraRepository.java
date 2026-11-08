package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Compra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompraRepository extends JpaRepository<Compra, UUID> {
}
