package com.ticketya.ventas.model;

import com.ticketya.ventas.model.enums.EstadoCompra;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compra orquestada por ms-ventas (saga). UNIQUE(cliente_id, idempotency_key) + request_hash
 * garantizan que un reintento con la misma clave no cobre dos veces.
 */
@Entity
@Table(name = "compra",
        uniqueConstraints = @UniqueConstraint(name = "uk_compra_cliente_idempotency",
                columnNames = {"cliente_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Id de la reserva en ms-reservas. */
    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    /** Id externo del cliente (header X-User-Id). */
    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    /** Id del evento en ms-eventos. */
    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCompra estado = EstadoCompra.PENDIENTE;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @OneToOne(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private Pago pago;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Entrada> entradas = new ArrayList<>();

    public void setPagoBidireccional(Pago pago) {
        pago.setCompra(this);
        this.pago = pago;
    }

    public void agregarEntrada(Entrada entrada) {
        entrada.setCompra(this);
        entradas.add(entrada);
    }

    @PrePersist
    void prePersist() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }
}
