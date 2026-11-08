package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Entrada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {
}
