package com.ticketya.ventas.model;

import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reintegro de dinero por devolucion aprobada o por cancelacion del evento.
 * <p>
 * UNIQUE(entrada_id, tipo): a lo sumo un reintegro de cada tipo por entrada, lo que hace idempotente
 * la cancelacion del evento. Un reintento de reintegro fallido debe reutilizar el registro existente.
 */
@Entity
@Table(name = "reintegro",
        uniqueConstraints = @UniqueConstraint(name = "uk_reintegro_entrada_tipo", columnNames = {"entrada_id", "tipo"}))
@Getter
@Setter
@NoArgsConstructor
public class Reintegro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrada_id", nullable = false)
    private Entrada entrada;

    /** Nullable: los reintegros por cancelacion de evento no tienen devolucion. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucion_id", unique = true)
    private Devolucion devolucion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoReintegro tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReintegro estado = EstadoReintegro.PENDIENTE;

    @Column(name = "transaccion_pasarela")
    private String transaccionPasarela;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
