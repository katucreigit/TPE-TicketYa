package com.ticketya.ventas.model;

import com.ticketya.ventas.model.enums.EstadoDevolucion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Solicitud de devolucion de una entrada. Una sola devolucion activa por entrada
 * (se valida en la logica de negocio).
 */
@Entity
@Table(name = "devolucion", indexes = {
        @Index(name = "idx_devolucion_entrada", columnList = "entrada_id"),
        @Index(name = "idx_devolucion_estado", columnList = "estado")
})
@Getter
@Setter
@NoArgsConstructor
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrada_id", nullable = false)
    private Entrada entrada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDevolucion estado = EstadoDevolucion.SOLICITADA;

    private String motivo;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    /** Id del administrador que la resolvio. */
    @Column(name = "resuelta_por")
    private String resueltaPor;

    @Column(name = "solicitada_en", nullable = false, updatable = false)
    private LocalDateTime solicitadaEn;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;

    @OneToOne(mappedBy = "devolucion")
    private Reintegro reintegro;

    @PrePersist
    void prePersist() {
        if (solicitadaEn == null) {
            solicitadaEn = LocalDateTime.now();
        }
    }
}
