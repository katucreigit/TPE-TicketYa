package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.Asiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsientoRepository extends JpaRepository<Asiento, Long> {
}
