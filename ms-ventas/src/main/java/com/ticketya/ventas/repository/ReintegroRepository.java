package com.ticketya.ventas.repository;

import com.ticketya.ventas.model.Reintegro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReintegroRepository extends JpaRepository<Reintegro, UUID> {
}
