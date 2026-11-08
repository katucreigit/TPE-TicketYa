package com.ticketya.eventos.model;

import com.ticketya.eventos.model.enums.TipoSector;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * NUMERADO usa filas y asientosPorFila; NO_NUMERADO usa capacidad.
 */
@Entity
@Table(name = "sector")
@Getter
@Setter
@NoArgsConstructor
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lugar_id", nullable = false)
    private Lugar lugar;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSector tipo;

    /** Solo no numerado. */
    private Integer capacidad;

    /** Solo numerado. */
    private Integer filas;

    /** Solo numerado. */
    @Column(name = "asientos_por_fila")
    private Integer asientosPorFila;
}
