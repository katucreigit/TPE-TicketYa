package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DevolucionRepository extends JpaRepository<Devolucion, UUID> {
}
