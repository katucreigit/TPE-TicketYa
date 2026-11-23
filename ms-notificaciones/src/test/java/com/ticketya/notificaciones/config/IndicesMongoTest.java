package com.ticketya.notificaciones.config;

import com.ticketya.notificaciones.IntegracionTest;
import com.ticketya.notificaciones.model.Destinatario;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 2.2 Indices creados al arrancar: unico y parcial sobre la clave, y los dos compuestos de consulta. */
class IndicesMongoTest extends IntegracionTest {

    @Test
    void elIndiceDeLaClaveEsUnicoYParcial() {
        IndexInfo clave = indice(IndicesMongo.IDX_CLAVE);
        assertThat(clave.isUnique()).isTrue();
        assertThat(clave.getPartialFilterExpression()).contains("$type").contains("string");
        assertThat(clave.getIndexFields()).extracting(IndexField::getKey).containsExactly("claveDeduplicacion");
    }

    @Test
    void existenLosIndicesCompuestosDeConsulta() {
        assertThat(indice(IndicesMongo.IDX_PENDIENTES).getIndexFields()).extracting(IndexField::getKey)
                .containsExactly("estado", "proximoIntentoEn");
        assertThat(indice(IndicesMongo.IDX_USUARIO).getIndexFields()).extracting(IndexField::getKey)
                .containsExactly("destinatario.usuarioId", "estado", "creadaEn");
    }

    @Test
    void dosDocumentosSinClaveNoChocan() {
        mongo.insert(documento(null));
        mongo.insert(documento(null));
        assertThat(contar()).isEqualTo(2);
    }

    @Test
    void dosDocumentosConLaMismaClaveChocan() {
        mongo.insert(documento("compra-1"));
        assertThatThrownBy(() -> mongo.insert(documento("compra-1"))).isInstanceOf(DuplicateKeyException.class);
        assertThat(contar()).isEqualTo(1);
    }

    private IndexInfo indice(String nombre) {
        List<IndexInfo> indices = mongo.indexOps(Notificacion.class).getIndexInfo();
        return indices.stream().filter(i -> nombre.equals(i.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("No existe el indice " + nombre + " en " + indices));
    }

    private static Notificacion documento(String clave) {
        Notificacion n = new Notificacion();
        n.setTipo(TipoNotificacion.EVENTO_CANCELADO);
        n.setDestinatario(new Destinatario("7", "x@example.com"));
        n.setEstado(EstadoNotificacion.ENVIADA);
        n.setClaveDeduplicacion(clave);
        n.setCreadaEn(Instant.now());
        return n;
    }
}
