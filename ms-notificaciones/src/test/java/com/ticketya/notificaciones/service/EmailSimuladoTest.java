package com.ticketya.notificaciones.service;

import com.ticketya.notificaciones.config.NotificacionesProperties;
import com.ticketya.notificaciones.model.Destinatario;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 5.2 "Fallas simuladas configurables" con la configuracion por defecto (sin Spring). */
@DisplayName("Fallas simuladas configurables")
class EmailSimuladoTest {

    private final EmailSimulado canal = new EmailSimulado(new NotificacionesProperties());

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    @DisplayName("Destinatario que siempre falla")
    void destinatarioQueSiempreFalla(int intento) {
        assertThatThrownBy(() -> canal.enviar(para("falla@ticketya.test"), intento))
                .isInstanceOf(EnvioFallidoException.class);
    }

    @Test
    @DisplayName("Destinatario que falla una sola vez")
    void destinatarioQueFallaUnaSolaVez() {
        Notificacion n = para("falla-una-vez@ticketya.test");
        assertThatThrownBy(() -> canal.enviar(n, 1)).isInstanceOf(EnvioFallidoException.class);
        assertThatCode(() -> canal.enviar(n, 2)).doesNotThrowAnyException();
    }

    @Test
    void unDestinatarioComunNoFalla() {
        assertThatCode(() -> canal.enviar(para("cliente@example.com"), 1)).doesNotThrowAnyException();
    }

    private static Notificacion para(String email) {
        Notificacion n = new Notificacion();
        n.setId("n-1");
        n.setTipo(TipoNotificacion.COMPRA_CONFIRMADA);
        n.setDestinatario(new Destinatario("101", email));
        return n;
    }
}
