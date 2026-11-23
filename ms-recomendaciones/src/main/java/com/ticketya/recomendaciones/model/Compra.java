package com.ticketya.recomendaciones.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Una compra confirmada que avisa ms-ventas (design.md D2). El {@code _id} es el compraId: la unicidad de la base
 * hace idempotente el registro, tambien con avisos simultaneos. {@code eventoIds} lleva un indice multikey.
 */
@Document(collection = Compra.COLECCION)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Compra {

    public static final String COLECCION = "compras";

    @Id
    private String compraId;
    private String clienteId;
    private List<Long> eventoIds;
    private Instant registradaEn;
}
