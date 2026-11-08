package com.ticketya.ventas.model;

import com.ticketya.ventas.model.enums.EstadoEntrada;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entrada emitida con QR. Guarda snapshots de precio, fecha del evento y asiento
 * para no depender de ms-eventos / ms-reservas al mostrarla o al aplicar la regla de 72 horas.
 */
@Entity
@Table(name = "entrada",
        uniqueConstraints = @UniqueConstraint(name = "uk_entrada_codigo_qr", columnNames = "codigo_qr"),
        indexes = @Index(name = "idx_entrada_evento_estado", columnList = "evento_id, estado"))
@Getter
@Setter
@NoArgsConstructor
public class Entrada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    /** Id del evento en ms-eventos. */
    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    /** Id del sector en ms-eventos. */
    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    /** Nullable: solo sectores numerados. */
    private Integer fila;

    /** Nullable: solo sectores numerados. */
    private Integer numero;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** Snapshot para la regla de 72 horas. */
    @Column(name = "evento_fecha_hora", nullable = false)
    private LocalDateTime eventoFechaHora;

    @Column(name = "codigo_qr", nullable = false, length = 36)
    private String codigoQr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoEntrada estado = EstadoEntrada.EMITIDA;
}
