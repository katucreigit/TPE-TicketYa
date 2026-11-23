package com.ticketya.fila;

import com.ticketya.fila.service.ProcesadorFila;
import com.ticketya.fila.trazabilidad.CapturaLogs;
import com.ticketya.fila.trazabilidad.RegistroOperaciones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Almacenamiento de la fila no disponible (spec fila-virtual). Usa su propio contenedor de Redis y su propio contexto,
 * porque lo pausa con {@code docker pause}: Redis deja de responder y cada comando vence por el timeout de 500 ms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RedisNoDisponibleTest.RedisPropio.class, RelojControlable.Config.class})
@DirtiesContext
class RedisNoDisponibleTest {

    private static final long EVENTO = 42L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GenericContainer<?> redisContainer;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private ProcesadorFila procesador;

    private CapturaLogs logs;

    @BeforeEach
    void preparar() {
        reanudar();
        redis.execute(c -> {
            c.serverCommands().flushDb();
            return null;
        }, true);
        reloj.fijar(Instant.parse("2026-12-01T20:00:00Z"));
        logs = new CapturaLogs(RegistroOperaciones.class);
    }

    @AfterEach
    void reanudarAlTerminar() {
        reanudar();
        logs.close();
    }

    @Test
    @DisplayName("Almacenamiento de la fila no disponible: Ingreso sin almacenamiento")
    void ingresoSinAlmacenamiento() throws Exception {
        pausar();

        mvc.perform(post("/fila/eventos/{e}/ingresar", EVENTO)
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICIO_NO_DISPONIBLE"));
        mvc.perform(get("/internal/fila/eventos/{e}/clientes/{c}/estado", EVENTO, "101"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test
    @DisplayName("Almacenamiento de la fila no disponible: Recuperación del proceso de habilitación")
    void recuperacionDelProcesoDeHabilitacion() throws Exception {
        mvc.perform(post("/fila/eventos/{e}/activar", EVENTO)
                        .header("X-User-Id", "900").header("X-User-Rol", "ADMIN")
                        .contentType("application/json").content("{\"tamanoTanda\":10,\"segundosEntreTandas\":30}"))
                .andExpect(status().isOk());
        mvc.perform(post("/fila/eventos/{e}/ingresar", EVENTO)
                        .header("X-User-Id", "101").header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk());
        reloj.avanzar(Duration.ofSeconds(30));

        pausar();
        procesador.procesar();   // no lanza: registra la advertencia y sigue
        reanudar();

        assertThat(logs.operaciones("CLIENTE_HABILITADO")).isEmpty();
        procesador.procesar();
        assertThat(logs.operaciones("CLIENTE_HABILITADO")).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("clienteId", "101"));
    }

    private void pausar() {
        redisContainer.getDockerClient().pauseContainerCmd(redisContainer.getContainerId()).exec();
    }

    private void reanudar() {
        boolean pausado = Boolean.TRUE.equals(redisContainer.getDockerClient()
                .inspectContainerCmd(redisContainer.getContainerId()).exec().getState().getPaused());
        if (pausado) {
            redisContainer.getDockerClient().unpauseContainerCmd(redisContainer.getContainerId()).exec();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisPropio {
        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        }
    }
}
