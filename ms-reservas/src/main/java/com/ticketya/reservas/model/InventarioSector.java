package com.ticketya.reservas.model;

import com.ticketya.reservas.model.enums.TipoSector;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Snapshot del sector (tipo y capacidad) tomado al publicar el evento.
 * En sectores no numerados, {@code disponibles} se descuenta con un UPDATE condicional atomico;
 * el CHECK (disponibles >= 0) es la red de seguridad contra la sobreventa.
 */
@Entity
@Table(name = "inventario_sector",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventario_sector", columnNames = {"evento_id", "sector_id"}))
@Check(name = "ck_inventario_sector_disponibles", constraints = "disponibles >= 0")
@Getter
@Setter
@NoArgsConstructor
public class InventarioSector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private InventarioEvento inventarioEvento;

    /** Id del sector en ms-eventos. */
    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSector tipo;

    @Column(name = "capacidad_total", nullable = false)
    private int capacidadTotal;

    /** Cupo atomico, solo no numerado. */
    @Column(nullable = false)
    private int disponibles;

    @Version
    @Column(nullable = false)
    private Long version;
}
