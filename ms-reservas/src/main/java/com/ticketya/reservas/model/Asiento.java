package com.ticketya.reservas.model;

import com.ticketya.reservas.model.enums.EstadoAsiento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Un asiento de un sector numerado, pre-generado al publicar el evento.
 * Reservar es un UPDATE condicional por estado (ver arquitectura.md, seccion 3);
 * {@code @Version} actua como segunda barrera.
 */
@Entity
@Table(name = "asiento",
        uniqueConstraints = @UniqueConstraint(name = "uk_asiento_lugar",
                columnNames = {"evento_id", "sector_id", "fila", "numero"}),
        indexes = @Index(name = "idx_asiento_reserva", columnList = "reserva_id"))
@Getter
@Setter
@NoArgsConstructor
public class Asiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventario_sector_id", nullable = false)
    private InventarioSector inventarioSector;

    /** Id del evento en ms-eventos (denormalizado para el UPDATE condicional). */
    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    /** Id del sector en ms-eventos. */
    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    @Column(nullable = false)
    private int fila;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAsiento estado = EstadoAsiento.LIBRE;

    /** Reserva que lo tiene tomado (nullable). */
    @Column(name = "reserva_id")
    private UUID reservaId;

    @Version
    @Column(nullable = false)
    private Long version;
}
