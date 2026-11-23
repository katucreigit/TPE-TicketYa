package com.ticketya.notificaciones;

import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.service.CanalEnvio;
import com.ticketya.notificaciones.service.EmailSimulado;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Envuelve al {@link EmailSimulado} real: cuenta los intentos por notificacion y permite bloquear el envio
 * para simular un canal lento. Sin bloqueo se comporta igual que el canal real (incluidas sus fallas).
 */
public class CanalControlable implements CanalEnvio {

    private final EmailSimulado delegado;
    private final Map<String, AtomicInteger> llamadas = new ConcurrentHashMap<>();
    private volatile CountDownLatch bloqueo;

    public CanalControlable(EmailSimulado delegado) {
        this.delegado = delegado;
    }

    @Override
    public void enviar(Notificacion notificacion, int intento) {
        llamadas.computeIfAbsent(notificacion.getId(), k -> new AtomicInteger()).incrementAndGet();
        CountDownLatch b = bloqueo;
        if (b != null) {
            try {
                b.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        delegado.enviar(notificacion, intento);
    }

    /** A partir de ahora cada envio queda detenido hasta {@link #reiniciar()} (o 10 s). */
    public void bloquear() {
        bloqueo = new CountDownLatch(1);
    }

    /** Libera cualquier envio detenido y borra los contadores. */
    public void reiniciar() {
        CountDownLatch b = bloqueo;
        bloqueo = null;
        if (b != null) {
            b.countDown();
        }
        llamadas.clear();
    }

    public int llamadas(String notificacionId) {
        AtomicInteger c = llamadas.get(notificacionId);
        return c == null ? 0 : c.get();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        CanalControlable canalControlable(EmailSimulado emailSimulado) {
            return new CanalControlable(emailSimulado);
        }
    }
}
