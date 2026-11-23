package com.ticketya.recomendaciones.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un documento por cliente (design.md D2): el {@code _id} es el clienteId, asi que guardar reemplaza el documento
 * completo. Las listas se guardan sin repetidos y los artistas con la forma que escribio el cliente.
 */
@Document(collection = Preferencias.COLECCION)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Preferencias {

    public static final String COLECCION = "preferencias";

    @Id
    private String clienteId;
    private List<TipoEvento> tipos = new ArrayList<>();
    private List<String> artistas = new ArrayList<>();
    private String ciudad;
    private Instant actualizadaEn;

    public boolean vacias() {
        return tipos.isEmpty() && artistas.isEmpty() && ciudad == null;
    }
}
