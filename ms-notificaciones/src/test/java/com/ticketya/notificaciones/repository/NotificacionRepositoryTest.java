package com.ticketya.notificaciones.repository;

import com.ticketya.notificaciones.IntegracionTest;
import com.ticketya.notificaciones.model.Destinatario;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 2.1 El documento se guarda y se lee con {@code datos} anidados sin perder estructura. */
class NotificacionRepositoryTest extends IntegracionTest {

    @Test
    void guardaYLeeUnaNotificacionConDatosAnidados() {
        Map<String, Object> datos = Map.of(
                "compraId", 55,
                "entradas", List.of(Map.of("entradaId", 901, "sector", "Platea", "asiento", "F12")),
                "cambios", Map.of("fecha", Map.of("antes", "2026-12-01T21:00", "despues", "2026-12-08T21:00")));
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Notificacion n = new Notificacion();
        n.setTipo(TipoNotificacion.COMPRA_CONFIRMADA);
        n.setDestinatario(new Destinatario("101", "cliente@example.com"));
        n.setDatos(datos);
        // ENVIADA para que el procesador no la toque durante la prueba.
        n.setEstado(EstadoNotificacion.ENVIADA);
        n.setIntentos(1);
        n.setClaveDeduplicacion("compra-55");
        n.setCorrelationId("abc-123");
        n.setCreadaEn(ahora);
        n.setActualizadaEn(ahora);
        n.setEnviadaEn(ahora);

        String id = repository.save(n).getId();
        Notificacion leida = repository.findById(id).orElseThrow();

        assertThat(arbol(leida.getDatos())).isEqualTo(arbol(datos));
        assertThat(leida.getDestinatario()).isEqualTo(new Destinatario("101", "cliente@example.com"));
        assertThat(leida.getTipo()).isEqualTo(TipoNotificacion.COMPRA_CONFIRMADA);
        assertThat(leida.getCreadaEn()).isEqualTo(ahora);
        assertThat(repository.findByClaveDeduplicacion("compra-55")).map(Notificacion::getId).contains(id);
    }
}
