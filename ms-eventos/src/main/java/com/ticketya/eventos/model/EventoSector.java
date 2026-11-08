package com.ticketya.eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Precio de un sector para un evento. El sector debe pertenecer al lugar del evento
 * (se valida en la logica de negocio).
 */
@Entity
@Table(name = "evento_sector",
        uniqueConstraints = @UniqueConstraint(name = "uk_evento_sector", columnNames = {"evento_id", "sector_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EventoSector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;
}
