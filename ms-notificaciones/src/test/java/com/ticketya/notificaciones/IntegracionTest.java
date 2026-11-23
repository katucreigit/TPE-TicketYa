package com.ticketya.notificaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.notificaciones.model.EstadoNotificacion;
import com.ticketya.notificaciones.model.Notificacion;
import com.ticketya.notificaciones.repository.NotificacionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de las pruebas de punta a punta: servidor real (puerto aleatorio), MongoDB real (Testcontainers) y el
 * procesador corriendo cada 100 ms con esperas de reintento de 200 ms (perfil test). Todas las subclases comparten
 * la misma configuracion, asi que comparten el contexto y el contenedor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, CanalControlable.Config.class})
public abstract class IntegracionTest {

    protected static final String INTERNO = "/internal/notificaciones";
    protected static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected MongoTemplate mongo;
    @Autowired
    protected NotificacionRepository repository;
    @Autowired
    protected CanalControlable canal;
    @Autowired
    protected ObjectMapper json;

    @BeforeEach
    void limpiarBase() {
        canal.reiniciar();
        mongo.remove(new Query(), Notificacion.class);
    }

    @AfterEach
    void liberarCanal() {
        canal.reiniciar();
    }

    /** Cuerpo de un pedido; {@code null} omite el campo. */
    protected String pedido(String tipo, String usuarioId, String email, Map<String, Object> datos, String clave) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (tipo != null) {
            cuerpo.put("tipo", tipo);
        }
        Map<String, Object> destinatario = new LinkedHashMap<>();
        if (usuarioId != null) {
            destinatario.put("usuarioId", usuarioId);
        }
        if (email != null) {
            destinatario.put("email", email);
        }
        cuerpo.put("destinatario", destinatario);
        if (datos != null) {
            cuerpo.put("datos", datos);
        }
        if (clave != null) {
            cuerpo.put("claveDeduplicacion", clave);
        }
        try {
            return json.writeValueAsString(cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected String pedidoCompra(String email, String clave) {
        return pedido("COMPRA_CONFIRMADA", "101", email, Map.of("compraId", 55, "total", 30000), clave);
    }

    protected ResultActions postInterno(String cuerpo) throws Exception {
        return postInterno(cuerpo, null);
    }

    protected ResultActions postInterno(String cuerpo, String correlationId) throws Exception {
        MockHttpServletRequestBuilder req = post(INTERNO).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (correlationId != null) {
            req.header("X-Correlation-Id", correlationId);
        }
        return mvc.perform(req);
    }

    /** Envia el pedido, exige 202 y devuelve el id de la notificacion. */
    protected String aceptar(String cuerpo) throws Exception {
        String respuesta = postInterno(cuerpo).andReturn().getResponse().getContentAsString();
        JsonNode nodo = json.readTree(respuesta);
        return nodo.get("id").asText();
    }

    protected Notificacion esperarEstado(String id, EstadoNotificacion estado) {
        return await().atMost(ESPERA_MAXIMA).until(
                () -> repository.findById(id).orElse(null),
                n -> n != null && n.getEstado() == estado);
    }

    /** Arbol JSON de un valor, para comparar estructuras sin importar el tipo concreto de Map o numero. */
    protected JsonNode arbol(Object valor) {
        return json.valueToTree(valor);
    }

    protected long contar() {
        return mongo.count(new Query(), Notificacion.class);
    }
}
