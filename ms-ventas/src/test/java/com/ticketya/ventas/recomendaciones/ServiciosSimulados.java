package com.ticketya.ventas.recomendaciones;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Servidores HTTP reales que simulan ms-eventos, ms-reservas, ms-notificaciones y ms-recomendaciones para las pruebas
 * del aviso de compras (mismas respuestas que {@code PropagacionCorrelacionTest}). ms-recomendaciones guarda cada
 * pedido y su demora se puede configurar.
 */
final class ServiciosSimulados {

    static final long EVENTO = 42L;
    static final long SECTOR = 11L;

    record Aviso(String metodo, String ruta, String cuerpo, String correlationId) {
    }

    static final List<Aviso> AVISOS = new CopyOnWriteArrayList<>();
    static volatile long demoraRecomendacionesMs = 0;
    static volatile int statusRecomendaciones = 204;

    static final HttpServer MS_EVENTOS = iniciar((metodo, ruta, cuerpo, corr) -> {
        if (ruta.endsWith("/sectores")) {
            return new Respuesta(200, "[{\"sectorId\":" + SECTOR + ",\"precio\":45000.00}]");
        }
        return new Respuesta(200, "{\"id\":" + EVENTO + ",\"estado\":\"PUBLICADO\",\"fechaHora\":\"2028-06-15T21:00:00\"}");
    });

    static final HttpServer MS_RESERVAS = iniciar((metodo, ruta, cuerpo, corr) -> {
        if ("GET".equals(metodo) && ruta.startsWith("/reservas/")) {
            String id = ruta.substring("/reservas/".length());
            return new Respuesta(200, """
                    {
                      "reservaId": "%s",
                      "eventoId": %d,
                      "sectorId": %d,
                      "clienteId": "101",
                      "estado": "PENDIENTE",
                      "cantidad": 1,
                      "items": [{"fila": 1, "numero": 1}],
                      "expiraEn": "2028-06-15T20:00:00"
                    }""".formatted(id, EVENTO, SECTOR));
        }
        return new Respuesta(200, null);
    });

    static final HttpServer MS_NOTIFICACIONES = iniciar((metodo, ruta, cuerpo, corr) -> new Respuesta(202, null));

    static final HttpServer MS_RECOMENDACIONES = iniciar((metodo, ruta, cuerpo, corr) -> {
        AVISOS.add(new Aviso(metodo, ruta, cuerpo, corr));
        if (demoraRecomendacionesMs > 0) {
            try {
                Thread.sleep(demoraRecomendacionesMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return new Respuesta(statusRecomendaciones, statusRecomendaciones == 204 ? null : "{\"code\":\"ERROR_INTERNO\"}");
    });

    private ServiciosSimulados() {
    }

    static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    static void reiniciar() {
        AVISOS.clear();
        demoraRecomendacionesMs = 0;
        statusRecomendaciones = 204;
    }

    static String cuerpoCompra(java.util.UUID reservaId, String token) {
        return """
                {
                  "reservaId": "%s",
                  "medioPago": {"tipo": "TARJETA_CREDITO", "token": "%s", "ultimos4": "4242"}
                }""".formatted(reservaId, token);
    }

    private record Respuesta(int status, String json) {
    }

    @FunctionalInterface
    private interface Manejador {
        Respuesta responder(String metodo, String ruta, String cuerpo, String correlationId);
    }

    private static HttpServer iniciar(Manejador manejador) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            // Varios hilos: una respuesta demorada no frena a las demas.
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", intercambio -> {
                try {
                    String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Respuesta resp = manejador.responder(intercambio.getRequestMethod(),
                            intercambio.getRequestURI().getPath(), cuerpo,
                            intercambio.getRequestHeaders().getFirst("X-Correlation-Id"));
                    if (resp.json() != null) {
                        byte[] bytes = resp.json().getBytes(StandardCharsets.UTF_8);
                        intercambio.getResponseHeaders().set("Content-Type", "application/json");
                        intercambio.sendResponseHeaders(resp.status(), bytes.length);
                        try (OutputStream out = intercambio.getResponseBody()) {
                            out.write(bytes);
                        }
                    } else {
                        intercambio.sendResponseHeaders(resp.status(), -1);
                    }
                } catch (IOException ex) {
                    // El cliente corto por timeout: no hay a quien responder.
                } finally {
                    intercambio.close();
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
