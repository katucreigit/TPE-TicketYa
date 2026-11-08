package com.ticketya.eventos.model;

import com.ticketya.eventos.model.enums.TipoParticipante;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "participante", indexes = @Index(name = "idx_participante_nombre", columnList = "nombre"))
@Getter
@Setter
@NoArgsConstructor
public class Participante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoParticipante tipo;
}
