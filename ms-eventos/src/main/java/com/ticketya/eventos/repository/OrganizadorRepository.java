package com.ticketya.eventos.repository;

import com.ticketya.eventos.model.Organizador;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizadorRepository extends JpaRepository<Organizador, Long> {
}
