package com.ticketya.recomendaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.recomendaciones.model.Compra;
import com.ticketya.recomendaciones.model.Preferencias;
import com.ticketya.recomendaciones.service.CatalogoEventos;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de las pruebas de integracion: servidor real en puerto aleatorio, MongoDB real (Testcontainers), reloj
 * controlable y ms-eventos simulado. Todas las subclases comparten contexto, contenedor y simulado (este vive lo
 * que dura la JVM de pruebas); cada prueba
 * empieza con las colecciones vacias, el simulado sin eventos y sin copia del catalogo en memoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RelojControlable.Config.class})
public abstract class IntegracionTest {

    protected static final Instant T0 = Instant.parse("2026-12-01T20:00:00Z");
    protected static final LocalDateTime FECHA = LocalDateTime.parse("2027-03-01T21:00:00");
    protected static final MsEventosSimulado MS_EVENTOS = new MsEventosSimulado();

    @DynamicPropertySource
    static void apuntarAlMsEventosSimulado(DynamicPropertyRegistry registro) {
        registro.add("ticketya.services.eventos-url", MS_EVENTOS::url);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected MongoTemplate mongo;
    @Autowired
    protected RelojControlable reloj;
    @Autowired
    protected CatalogoEventos catalogo;
    @Autowired
    protected ObjectMapper json;
    @LocalServerPort
    protected int puerto;

    @BeforeEach
    void prepararBase() {
        mongo.remove(new Query(), Compra.class);
        mongo.remove(new Query(), Preferencias.class);
        MS_EVENTOS.reiniciar();
        catalogo.olvidar();
        reloj.fijar(T0);
    }

    // ------------------------------------------------------------------ solicitudes

    protected ResultActions putPreferencias(String clienteId, String cuerpo) throws Exception {
        return mvc.perform(identidad(put("/preferencias"), clienteId, "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    protected ResultActions getPreferencias(String clienteId) throws Exception {
        return mvc.perform(identidad(get("/preferencias"), clienteId, "CLIENTE"));
    }

    protected ResultActions recomendaciones(String clienteId) throws Exception {
        return mvc.perform(identidad(get("/recomendaciones"), clienteId, "CLIENTE"));
    }

    protected ResultActions tambienCompraron(long eventoId) throws Exception {
        return mvc.perform(identidad(get("/recomendaciones/eventos/{e}/tambien-compraron", eventoId), "900", "CLIENTE"));
    }

    protected ResultActions registrarCompra(String compraId, String clienteId, Long... eventoIds) throws Exception {
        String ids = Arrays.stream(eventoIds).map(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(post("/internal/compras").contentType(MediaType.APPLICATION_JSON)
                .content("{\"compraId\":\"" + compraId + "\",\"clienteId\":\"" + clienteId
                        + "\",\"eventoIds\":[" + ids + "]}"));
    }

    /** Registra una compra por evento del cliente (cada una con su propio compraId). */
    protected void compro(String clienteId, Long... eventoIds) throws Exception {
        for (Long e : eventoIds) {
            registrarCompra("c-" + clienteId + "-" + e, clienteId, e);
        }
    }

    protected JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    protected static MockHttpServletRequestBuilder identidad(MockHttpServletRequestBuilder req, String userId, String rol) {
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        if (rol != null) {
            req.header("X-User-Rol", rol);
        }
        return req;
    }
}
