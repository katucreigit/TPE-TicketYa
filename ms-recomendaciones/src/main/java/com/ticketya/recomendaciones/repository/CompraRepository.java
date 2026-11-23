package com.ticketya.recomendaciones.repository;

import com.ticketya.recomendaciones.model.Compra;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compras registradas y las tres consultas de design.md D4, resueltas con el aggregation pipeline. Todos los conteos
 * son de clientes distintos: un cliente que compro dos veces el mismo evento cuenta una sola vez.
 */
@Repository
public class CompraRepository {

    private final MongoTemplate mongo;

    public CompraRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Inserta la compra; lanza DuplicateKeyException si el compraId ya existe. */
    public void insertar(Compra compra) {
        mongo.insert(compra);
    }

    public long contar() {
        return mongo.count(new Query(), Compra.class);
    }

    /** Eventos que compro el cliente. */
    public Set<Long> eventosDe(String clienteId) {
        return new HashSet<>(mongo.findDistinct(Query.query(Criteria.where("clienteId").is(clienteId)),
                "eventoIds", Compra.class, Long.class));
    }

    /** eventoId -> cantidad de clientes distintos que lo compraron. */
    public Map<Long, Long> compradoresPorEvento() {
        return contarPorEvento(List.of(
                new Document("$unwind", "$eventoIds"),
                new Document("$group", new Document("_id",
                        new Document("e", "$eventoIds").append("c", "$clienteId"))),
                new Document("$group", new Document("_id", "$_id.e").append("n", new Document("$sum", 1)))));
    }

    /**
     * Para los eventos que no estan en {@code eventos}: cuantos clientes distintos que compraron alguno de
     * {@code eventos} tambien los compraron. {@code excluido} (puede ser null) no cuenta como comprador.
     */
    public Map<Long, Long> compradoresEnComun(Set<Long> eventos, String excluido) {
        if (eventos.isEmpty()) {
            return Map.of();
        }
        Criteria compraronAlguno = Criteria.where("eventoIds").in(eventos);
        if (excluido != null) {
            compraronAlguno = compraronAlguno.and("clienteId").ne(excluido);
        }
        List<String> clientes = mongo.findDistinct(Query.query(compraronAlguno), "clienteId", Compra.class, String.class);
        if (clientes.isEmpty()) {
            return Map.of();
        }
        return contarPorEvento(List.of(
                new Document("$match", new Document("clienteId", new Document("$in", clientes))),
                new Document("$unwind", "$eventoIds"),
                new Document("$match", new Document("eventoIds", new Document("$nin", new ArrayList<>(eventos)))),
                new Document("$group", new Document("_id",
                        new Document("e", "$eventoIds").append("c", "$clienteId"))),
                new Document("$group", new Document("_id", "$_id.e").append("n", new Document("$sum", 1)))));
    }

    private Map<Long, Long> contarPorEvento(List<Document> pipeline) {
        Map<Long, Long> conteo = new HashMap<>();
        for (Document d : mongo.getCollection(Compra.COLECCION).aggregate(pipeline)) {
            conteo.put(((Number) d.get("_id")).longValue(), ((Number) d.get("n")).longValue());
        }
        return conteo;
    }
}
