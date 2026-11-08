package com.ticketya.eventos.repository;

import com.ticketya.eventos.model.Lugar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LugarRepository extends JpaRepository<Lugar, Long> {
}
