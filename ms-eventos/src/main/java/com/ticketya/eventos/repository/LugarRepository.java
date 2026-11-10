package com.ticketya.eventos.repository;

import com.ticketya.eventos.model.Lugar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LugarRepository extends JpaRepository<Lugar, Long> {

    Page<Lugar> findByCiudadIgnoreCase(String ciudad, Pageable pageable);
}
