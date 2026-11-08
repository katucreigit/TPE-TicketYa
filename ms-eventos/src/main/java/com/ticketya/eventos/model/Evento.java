package com.ticketya.eventos.model;

import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.model.enums.TipoEvento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "evento", indexes = {
        @Index(name = "idx_evento_estado_fecha", columnList = "estado, fecha_hora"),
        @Index(name = "idx_evento_tipo", columnList = "tipo"),
        @Index(name = "idx_evento_organizador", columnList = "organizador_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 2000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizador_id", nullable = false)
    private Organizador organizador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lugar_id", nullable = false)
    private Lugar lugar;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEvento estado = EstadoEvento.BORRADOR;

    @Column(name = "max_entradas_por_cliente", nullable = false)
    private int maxEntradasPorCliente = 4;

    @Column(name = "alta_demanda", nullable = false)
    private boolean altaDemanda;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Tabla evento_participante con PK(evento_id, participante_id). */
    @ManyToMany
    @JoinTable(name = "evento_participante",
            joinColumns = @JoinColumn(name = "evento_id"),
            inverseJoinColumns = @JoinColumn(name = "participante_id"))
    private Set<Participante> participantes = new LinkedHashSet<>();

    /** Precios por sector del evento. */
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventoSector> sectores = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
