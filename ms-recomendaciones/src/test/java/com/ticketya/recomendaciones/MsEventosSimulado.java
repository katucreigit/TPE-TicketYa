package com.ticketya.recomendaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * ms-eventos simulado: un HttpServer real en un puerto libre que sirve {@code GET /eventos} paginado con los eventos
 * cargados por la prueba. El modo controla si responde bien, con 500, corta la conexion o falla en una pagina.
 */
public final class MsEventosSimulado {

    public enum Modo { OK, ERROR_500, CORTE, FALLA_SEGUNDA_PAGINA }

    public record Pedido(String ruta, Map<String, String> query, String correlationId) {
    }

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final HttpServer server;
    private final List<Map<String, Object>> eventos = new CopyOnWriteArrayList<>();
    private final List<Pedido> pedidos = new CopyOnWriteArrayList<>();
    private volatile Modo modo = Modo.OK;

    public MsEventosSimulado() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", intercambio -> {
            Map<String, String> query = query(intercambio.getRequestURI());
            pedidos.add(new Pedido(intercambio.getRequestURI().getPath(), query,
                    intercambio.getRequestHeaders().getFirst("X-Correlation-Id")));
            int pagina = Integer.parseInt(query.getOrDefault("page", "0"));
            int tamano = Integer.parseInt(query.getOrDefault("size", "20"));
            try {
                if (modo == Modo.CORTE) {
                    return;   // cierra sin responder
                }
                if (modo == Modo.ERROR_500 || (modo == Modo.FALLA_SEGUNDA_PAGINA && pagina >= 1)) {
                    responder(intercambio, 500, "{\"code\":\"ERROR_INTERNO\"}");
                    return;
                }
                int total = eventos.size();
                int paginas = Math.max(1, (total + tamano - 1) / tamano);
                List<Map<String, Object>> contenido = eventos.subList(Math.min(total, pagina * tamano),
                        Math.min(total, (pagina + 1) * tamano));
                Map<String, Object> cuerpo = new LinkedHashMap<>();
                cuerpo.put("content", new ArrayList<>(contenido));
                cuerpo.put("page", pagina);
                cuerpo.put("size", tamano);
                cuerpo.put("totalElements", total);
                cuerpo.put("totalPages", paginas);
                responder(intercambio, 200, JSON.writeValueAsString(cuerpo));
            } finally {
                intercambio.close();
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reiniciar() {
        eventos.clear();
        pedidos.clear();
        modo = Modo.OK;
    }

    public void modo(Modo nuevo) {
        this.modo = nuevo;
    }

    public List<Pedido> pedidos() {
        return pedidos;
    }

    /** Agrega un evento con el formato de EventoResumenResponse de ms-eventos. */
    public void evento(long id, String tipo, String estado, String ciudad, LocalDateTime fecha, String... participantes) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("nombre", "Evento " + id);
        e.put("tipo", tipo);
        e.put("estado", estado);
        e.put("ciudad", ciudad);
        e.put("fechaHora", fecha);
        e.put("precioDesde", 1000);
        e.put("participantes", List.of(participantes));
        eventos.add(e);
    }

    public void publicado(long id, String tipo, String ciudad, LocalDateTime fecha, String... participantes) {
        evento(id, tipo, "PUBLICADO", ciudad, fecha, participantes);
    }

    public void detener() {
        server.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, int status, String cuerpo)
            throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(status, bytes.length);
        intercambio.getResponseBody().write(bytes);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> valores = new HashMap<>();
        if (uri.getRawQuery() != null) {
            for (String par : uri.getRawQuery().split("&")) {
                String[] kv = par.split("=", 2);
                valores.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return valores;
    }
}
