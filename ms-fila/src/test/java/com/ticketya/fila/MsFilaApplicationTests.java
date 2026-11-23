package com.ticketya.fila;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 1.3 El contexto arranca contra un Redis real. */
class MsFilaApplicationTests extends IntegracionTest {

    @Test
    void elContextoArrancaYRedisResponde() {
        String pong = redis.execute(conexion -> conexion.ping(), true);
        assertThat(pong).isEqualTo("PONG");
    }
}
