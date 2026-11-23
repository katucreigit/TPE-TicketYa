package com.ticketya.fila;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.fila.repository.FilaRepository;
import com.ticketya.fila.service.ProcesadorFila;
import com.ticketya.fila.trazabilidad.CapturaLogs;
import com.ticketya.fila.trazabilidad.RegistroOperaciones;
import com.ticketya.fila.trazabilidad.RegistroSolicitudesFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de las pruebas: servidor real en puerto aleatorio (para las pruebas concurrentes por HTTP), MockMvc con los
 * filtros de trazabilidad, Redis real (Testcontainers), reloj controlable y el scheduler apagado (perfil test).
 * Todas las subclases comparten la configuracion, asi que comparten el contexto y el contenedor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RelojControlable.Config.class})
public abstract class IntegracionTest {

    protected static final Instant T0 = Instant.parse("2026-12-01T20:00:00Z");
    protected static final long EVENTO = 42L;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected RelojControlable reloj;
    @Autowired
    protected ProcesadorFila procesador;
    @Autowired
    protected ObjectMapper json;
    @LocalServerPort
    protected int puerto;

    protected CapturaLogs logs;

    @BeforeEach
    void prepararBase() {
        redis.execute(conexion -> {
            conexion.serverCommands().flushDb();
            return null;
        }, true);
        reloj.fijar(T0);
        logs = new CapturaLogs(RegistroOperaciones.class, RegistroSolicitudesFilter.class);
    }

    @AfterEach
    void cerrarLogs() {
        logs.close();
    }

    // ------------------------------------------------------------------ solicitudes

    protected ResultActions activar(long eventoId, String cuerpo) throws Exception {
        return activar(eventoId, cuerpo, "900", "ADMIN");
    }

    protected ResultActions activar(long eventoId, String cuerpo, String userId, String rol) throws Exception {
        MockHttpServletRequestBuilder req = identidad(post("/fila/eventos/{e}/activar", eventoId), userId, rol);
        if (cuerpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(req);
    }

    /** Activa con el tamaño y el intervalo dados; la primera tanda queda en T0 + segundos. */
    protected void activarFila(long eventoId, int tamano, int segundos) throws Exception {
        activar(eventoId, "{\"tamanoTanda\":" + tamano + ",\"segundosEntreTandas\":" + segundos + "}")
                .andReturn();
    }

    protected ResultActions desactivar(long eventoId, String userId, String rol) throws Exception {
        return mvc.perform(identidad(post("/fila/eventos/{e}/desactivar", eventoId), userId, rol));
    }

    protected ResultActions ingresar(long eventoId, String clienteId) throws Exception {
        return ingresar(eventoId, clienteId, "CLIENTE");
    }

    protected ResultActions ingresar(long eventoId, String userId, String rol) throws Exception {
        return mvc.perform(identidad(post("/fila/eventos/{e}/ingresar", eventoId), userId, rol));
    }

    protected ResultActions posicion(long eventoId, String clienteId) throws Exception {
        return mvc.perform(identidad(get("/fila/eventos/{e}/posicion", eventoId), clienteId, "CLIENTE"));
    }

    protected ResultActions estadoInterno(long eventoId, String clienteId) throws Exception {
        return mvc.perform(get("/internal/fila/eventos/{e}/clientes/{c}/estado", eventoId, clienteId));
    }

    protected JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder identidad(MockHttpServletRequestBuilder req, String userId, String rol) {
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        if (rol != null) {
            req.header("X-User-Rol", rol);
        }
        return req;
    }

    // ------------------------------------------------------------------ estado en Redis

    protected long enEspera(long eventoId) {
        Long n = redis.opsForZSet().zCard(FilaRepository.espera(eventoId));
        return n == null ? 0 : n;
    }

    protected long habilitados(long eventoId) {
        Long n = redis.opsForZSet().zCard(FilaRepository.habilitados(eventoId));
        return n == null ? 0 : n;
    }

    /** Adelanta el reloj y corre una vez el proceso de la fila. */
    protected void pasar(Duration duracion) {
        reloj.avanzar(duracion);
        procesador.procesar();
    }
}
