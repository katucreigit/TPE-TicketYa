package com.ticketya.recomendaciones.config;

import com.ticketya.recomendaciones.model.Compra;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Crea los indices de las compras al arrancar, antes de que el servidor acepte solicitudes (design.md D2 y D4).
 * La unicidad del compraId la da el {@code _id}; estos indices sirven al primer {@code $match} de cada agregacion.
 */
@Component
public class IndicesMongo implements InitializingBean {

    public static final String IDX_CLIENTE = "ix_clienteId";
    public static final String IDX_EVENTOS = "ix_eventoIds";

    private final MongoTemplate mongo;

    public IndicesMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void afterPropertiesSet() {
        IndexOperations ops = mongo.indexOps(Compra.class);
        ops.ensureIndex(new Index().on("clienteId", Sort.Direction.ASC).named(IDX_CLIENTE));
        // Multikey: un indice sobre un arreglo indexa cada elemento.
        ops.ensureIndex(new Index().on("eventoIds", Sort.Direction.ASC).named(IDX_EVENTOS));
    }
}
