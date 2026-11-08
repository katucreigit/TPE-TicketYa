package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PagoRepository extends JpaRepository<Pago, UUID> {
}
