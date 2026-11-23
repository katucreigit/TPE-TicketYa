package com.ticketya.notificaciones.service;

import com.ticketya.notificaciones.TestcontainersConfiguration;
import com.ticketya.notificaciones.dto.DestinatarioDto;
import com.ticketya.notificaciones.dto.NotificacionRequest;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.model.TipoNotificacion;
import com.ticketya.notificaciones.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reintentos con un reloj controlado y el procesador apagado: cada ciclo se dispara a mano, asi que las esperas se
 * verifican sin dormir. Usa la espera por defecto (2 s, factor 2, 3 intentos).
 */
@SpringBootTest(properties = {
        "ticketya.notificaciones.procesador.habilitado=false",
        "ticketya.notificaciones.reintentos.espera-inicial=2s"
})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ReintentosRelojControladoTest.Reloj.class})
class ReintentosRelojControladoTest {

    private static final Instant T0 = Instant.parse("2026-11-22T12:00:00Z");

    @Autowired
    private RelojControlado reloj;
    @Autowired
    private NotificacionService service;
    @Autowired
    private ProcesadorNotificaciones procesador;
    @Autowired
    private NotificacionRepository repository;
    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void preparar() {
        mongo.remove(new Query(), Notificacion.class);
        reloj.fijar(T0);
    }

    @Test
    @DisplayName("Reintentos con espera creciente: La espera crece entre reintentos")
    void laEsperaCreceEntreReintentos() {
        String id = service.recibir(pedido("falla@ticketya.test"), "corr-1").getId();

        List<Instant> intentos = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Instant ahora = reloj.instant();
            assertThat(procesador.procesarPendientes()).as("intento %d", i).isEqualTo(1);
            intentos.add(ahora);
            Notificacion n = repository.findById(id).orElseThrow();
            assertThat(n.getIntentos()).isEqualTo(i);
            if (i < 3) {
                assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
                // Antes de que venza la espera no se reintenta.
                reloj.fijar(n.getProximoIntentoEn().minusMillis(1));
                assertThat(procesador.procesarPendientes()).isZero();
                reloj.fijar(n.getProximoIntentoEn());
            } else {
                assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
            }
        }

        Duration primera = Duration.between(intentos.get(0), intentos.get(1));
        Duration segunda = Duration.between(intentos.get(1), intentos.get(2));
        assertThat(primera).isEqualTo(Duration.ofSeconds(2));
        assertThat(segunda).isEqualTo(Duration.ofSeconds(4));
        assertThat(segunda).isGreaterThan(primera);
    }

    @Test
    void laEsperaEsExponencial() {
        assertThat(procesador.esperaTrasIntento(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(procesador.esperaTrasIntento(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(procesador.esperaTrasIntento(3)).isEqualTo(Duration.ofSeconds(8));
    }

    /** 5.9 Dos procesadores que compiten por la misma notificacion: solo uno la toma. */
    @RepeatedTest(5)
    void laTomaEsAtomica() throws Exception {
        String id = service.recibir(pedido("cliente@example.com"), "corr-2").getId();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Optional<Notificacion>>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return procesador.tomarSiguiente();
                }));
            }
            largada.countDown();
            List<Optional<Notificacion>> tomadas = new ArrayList<>();
            for (Future<Optional<Notificacion>> f : futuros) {
                tomadas.add(f.get(10, TimeUnit.SECONDS));
            }
            assertThat(tomadas).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(tomadas).filteredOn(Optional::isPresent).first()
                    .satisfies(o -> assertThat(o.orElseThrow().getId()).isEqualTo(id));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unaNotificacionTomadaNoVuelveAVerseHastaQueVenceLaReserva() {
        service.recibir(pedido("cliente@example.com"), "corr-3");
        assertThat(procesador.tomarSiguiente()).isPresent();
        assertThat(procesador.tomarSiguiente()).isEmpty();
        reloj.fijar(T0.plusSeconds(30));
        assertThat(procesador.tomarSiguiente()).isPresent();
    }

    private static NotificacionRequest pedido(String email) {
        return new NotificacionRequest(TipoNotificacion.COMPRA_CONFIRMADA, new DestinatarioDto("101", email),
                Map.of("compraId", 55), null);
    }

    /** Reloj que solo avanza cuando la prueba lo indica. */
    static class RelojControlado extends Clock {
        private final AtomicReference<Instant> ahora = new AtomicReference<>(T0);

        void fijar(Instant instante) {
            ahora.set(instante);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Reloj {
        @Bean
        @Primary
        RelojControlado relojControlado() {
            return new RelojControlado();
        }
    }
}
