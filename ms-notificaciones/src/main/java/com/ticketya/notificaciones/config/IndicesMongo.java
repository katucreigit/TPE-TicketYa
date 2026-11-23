package com.ticketya.notificaciones.config;

import com.ticketya.notificaciones.model.Notificacion;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * Crea los indices de la coleccion al arrancar, antes de que el servidor acepte solicitudes (design.md D2).
 * El indice unico de {@code claveDeduplicacion} es la garantia de deduplicacion, tambien con pedidos simultaneos.
 */
@Component
public class IndicesMongo implements InitializingBean {

    public static final String IDX_CLAVE = "ux_claveDeduplicacion";
    public static final String IDX_PENDIENTES = "ix_estado_proximoIntentoEn";
    public static final String IDX_USUARIO = "ix_usuario_estado_creadaEn";

    private final MongoTemplate mongo;

    public IndicesMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void afterPropertiesSet() {
        IndexOperations ops = mongo.indexOps(Notificacion.class);
        ops.ensureIndex(new Index()
                .on("claveDeduplicacion", Sort.Direction.ASC)
                .unique()
                // Parcial: solo los documentos con clave string participan; los que no tienen clave no chocan.
                .partial(PartialIndexFilter.of(new Document("claveDeduplicacion", new Document("$type", "string"))))
                .named(IDX_CLAVE));
        ops.ensureIndex(new Index()
                .on("estado", Sort.Direction.ASC)
                .on("proximoIntentoEn", Sort.Direction.ASC)
                .named(IDX_PENDIENTES));
        ops.ensureIndex(new Index()
                .on("destinatario.usuarioId", Sort.Direction.ASC)
                .on("estado", Sort.Direction.ASC)
                .on("creadaEn", Sort.Direction.DESC)
                .named(IDX_USUARIO));
    }
}
