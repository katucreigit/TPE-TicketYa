package com.ticketya.recomendaciones;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 1.3 El contexto arranca contra un MongoDB real. */
class MsRecomendacionesApplicationTests extends IntegracionTest {

    @Test
    void elContextoArrancaYMongoResponde() {
        Document ping = mongo.getDb().runCommand(new Document("ping", 1));
        assertThat(ping.get("ok", Number.class).intValue()).isEqualTo(1);
    }
}
