package com.ticketya.eventos.repository;

import com.ticketya.eventos.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.ticketya.eventos.model.enums.EstadoEvento;
import java.time.LocalDateTime;
import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long>,
        JpaSpecificationExecutor<Evento> {
        List<Evento> findByEstadoInAndFechaHoraBefore(
        List<EstadoEvento> estados,
        LocalDateTime fechaHora
);
}
