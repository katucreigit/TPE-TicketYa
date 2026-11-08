package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.InventarioEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventarioEventoRepository extends JpaRepository<InventarioEvento, Long> {
}
