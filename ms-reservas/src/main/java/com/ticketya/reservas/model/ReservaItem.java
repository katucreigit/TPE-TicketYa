package com.ticketya.reservas.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Detalle de la reserva: un asiento (cantidad 1) en sectores numerados,
 * o n cupos (asiento null) en sectores no numerados.
 */
@Entity
@Table(name = "reserva_item")
@Getter
@Setter
@NoArgsConstructor
public class ReservaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    /** Nullable: solo sectores numerados. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_id")
    private Asiento asiento;

    @Column(nullable = false)
    private int cantidad;
}
