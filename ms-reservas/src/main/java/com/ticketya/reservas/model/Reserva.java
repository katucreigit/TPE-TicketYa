package com.ticketya.reservas.model;

import com.ticketya.reservas.model.enums.EstadoReserva;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reserva temporal (10 minutos). Confirmar y vencer son transiciones mutuamente excluyentes
 * por UPDATE condicional sobre estado y expira_en.
 */
@Entity
@Table(name = "reserva", indexes = {
        @Index(name = "idx_reserva_estado_expira", columnList = "estado, expira_en"),
        @Index(name = "idx_reserva_cliente_evento", columnList = "cliente_id, evento_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Id del evento en ms-eventos. */
    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    /** Id del sector en ms-eventos. */
    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    /** Id externo del cliente (header X-User-Id). */
    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservaItem> items = new ArrayList<>();

    public void agregarItem(ReservaItem item) {
        item.setReserva(this);
        items.add(item);
    }

    @PrePersist
    void prePersist() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }
}
