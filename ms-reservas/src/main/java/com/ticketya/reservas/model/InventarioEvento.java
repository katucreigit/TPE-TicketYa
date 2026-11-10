package com.ticketya.reservas.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inventario de un evento. La PK es el id del evento en ms-eventos (referencia logica, sin FK).
 */
@Entity
@Table(name = "inventario_evento")
@Getter
@Setter
@NoArgsConstructor
public class InventarioEvento {

    @Id
    @Column(name = "evento_id")
    private Long eventoId;

    /** false al suspender o cancelar el evento: no admite nuevas reservas. */
    @Column(nullable = false)
    private boolean abierto = true;

    /** Snapshot de evento.max_entradas_por_cliente al publicar. */
    @Column(name = "max_por_cliente", nullable = false)
    private int maxPorCliente;

    /**
     * Ultimo valor de agotado que ms-eventos confirmo. Null: todavia no se notifico nada.
     * Si difiere del estado real, el job reintenta el aviso.
     */
    @Column(name = "agotado_notificado")
    private Boolean agotadoNotificado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "inventarioEvento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventarioSector> sectores = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
