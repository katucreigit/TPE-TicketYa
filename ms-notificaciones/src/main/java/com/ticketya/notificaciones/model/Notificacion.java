package com.ticketya.notificaciones.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un documento por notificacion (design.md D2). {@code datos} es un subdocumento libre cuya forma depende del tipo.
 * Los campos nulos no se escriben, asi que una notificacion sin clave no entra en el indice unico parcial.
 */
@Document(collection = Notificacion.COLECCION)
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

    public static final String COLECCION = "notificaciones";

    @Id
    private String id;
    private TipoNotificacion tipo;
    private Destinatario destinatario;
    private Map<String, Object> datos = new LinkedHashMap<>();
    private EstadoNotificacion estado;
    private int intentos;
    private String ultimoError;
    private String claveDeduplicacion;
    private String correlationId;
    private Instant creadaEn;
    private Instant actualizadaEn;
    private Instant proximoIntentoEn;
    private Instant enviadaEn;
}
