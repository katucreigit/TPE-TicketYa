package com.ticketya.fila.repository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;
import java.util.Set;

/**
 * Acceso a Redis de la fila virtual (design.md D2). Toda operacion que lee y escribe es un script Lua: Redis los
 * ejecuta de a uno, asi que son atomicos frente a cualquier cantidad de instancias de ms-fila, sin locks.
 * <p>
 * Las claves de una fila llevan el eventoId como hash tag ({@code fila:{42}:...}) para que los scripts sigan
 * siendo validos en Redis Cluster. La hora llega siempre como argumento (epoch ms), tomada del Clock (D6).
 */
@Repository
public class FilaRepository {

    /** Set con los eventoId de las filas activas; lo recorre el proceso de tandas. */
    public static final String ACTIVAS = "fila:activas";

    private static final RedisScript<List> ACTIVAR = script("activar");
    private static final RedisScript<List> DESACTIVAR = script("desactivar");
    private static final RedisScript<List> INGRESAR = script("ingresar");
    private static final RedisScript<List> LEER = script("leer");
    private static final RedisScript<List> HABILITAR_TANDA = script("habilitar-tanda");
    private static final RedisScript<List> VENCER_TURNOS = script("vencer-turnos");

    private final StringRedisTemplate redis;

    public FilaRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ------------------------------------------------------------------ resultados

    public record ResultadoActivar(boolean estabaActiva, int tamanoTanda, int segundosEntreTandas,
                                   int minutosVentana, long proximaTandaEn, long enEspera, long habilitados) {
    }

    public record ResultadoDesactivar(boolean estabaActiva, long enEspera, long habilitados) {
    }

    /** {@code habilitadoHasta} y {@code posicion} son null cuando no corresponden. */
    public record ResultadoIngreso(boolean activa, Long posicion, Long habilitadoHasta, boolean nuevo,
                                   boolean vencido) {
    }

    /** Lectura cruda: {@code habilitadoHasta} null si no esta en habilitados, {@code posicion} null si no espera. */
    public record Lectura(boolean activa, Long habilitadoHasta, Long posicion) {
    }

    public record Tanda(long habilitadoHasta, List<String> clientes) {
    }

    // ------------------------------------------------------------------ operaciones

    public ResultadoActivar activar(long eventoId, int tamanoTanda, int segundosEntreTandas, int minutosVentana,
                                    long ahora, long ttlSegundos) {
        redis.opsForSet().add(ACTIVAS, String.valueOf(eventoId));
        List<?> r = redis.execute(ACTIVAR, claves(eventoId),
                String.valueOf(tamanoTanda), String.valueOf(segundosEntreTandas), String.valueOf(minutosVentana),
                String.valueOf(ahora), String.valueOf(ttlSegundos));
        return new ResultadoActivar(entero(r, 0) == 1, (int) entero(r, 1), (int) entero(r, 2),
                (int) entero(r, 3), entero(r, 4), entero(r, 5), entero(r, 6));
    }

    public ResultadoDesactivar desactivar(long eventoId) {
        List<?> r = redis.execute(DESACTIVAR, claves(eventoId));
        redis.opsForSet().remove(ACTIVAS, String.valueOf(eventoId));
        return new ResultadoDesactivar(entero(r, 0) == 1, entero(r, 1), entero(r, 2));
    }

    public ResultadoIngreso ingresar(long eventoId, String clienteId, long ahora) {
        List<?> r = redis.execute(INGRESAR, claves(eventoId), clienteId, String.valueOf(ahora));
        long resultado = entero(r, 0);
        if (resultado == -1) {
            return new ResultadoIngreso(false, null, null, false, false);
        }
        if (resultado == 2) {
            return new ResultadoIngreso(true, null, entero(r, 3), false, false);
        }
        return new ResultadoIngreso(true, entero(r, 1), null, entero(r, 2) == 1, entero(r, 4) == 1);
    }

    public Lectura leer(long eventoId, String clienteId) {
        List<?> r = redis.execute(LEER, claves(eventoId).subList(0, 3), clienteId);
        if (entero(r, 0) == 0) {
            return new Lectura(false, null, null);
        }
        String hasta = String.valueOf(r.get(1));
        long posicion = entero(r, 2);
        return new Lectura(true, hasta.isEmpty() ? null : numero(hasta), posicion == 0 ? null : posicion);
    }

    /** Habilita la tanda si llego su momento; {@code null} si no correspondia. */
    public Tanda habilitarTanda(long eventoId, long ahora) {
        List<?> r = redis.execute(HABILITAR_TANDA, claves(eventoId), String.valueOf(ahora));
        if (r == null || r.isEmpty()) {
            return null;
        }
        List<String> clientes = r.subList(1, r.size()).stream().map(String::valueOf).toList();
        return new Tanda(entero(r, 0), clientes);
    }

    /** Quita hasta {@code maximo} turnos vencidos y devuelve a quienes quito. */
    public List<String> vencerTurnos(long eventoId, long ahora, int maximo) {
        List<?> r = redis.execute(VENCER_TURNOS, List.of(habilitados(eventoId)),
                String.valueOf(ahora), String.valueOf(maximo));
        return r == null ? List.of() : r.stream().map(String::valueOf).toList();
    }

    public Set<String> eventosActivos() {
        Set<String> activos = redis.opsForSet().members(ACTIVAS);
        return activos == null ? Set.of() : activos;
    }

    public boolean existe(long eventoId) {
        return Boolean.TRUE.equals(redis.hasKey(config(eventoId)));
    }

    public void quitarDeActivas(long eventoId) {
        redis.opsForSet().remove(ACTIVAS, String.valueOf(eventoId));
    }

    // ------------------------------------------------------------------ claves

    public static String config(long eventoId) {
        return prefijo(eventoId) + "config";
    }

    public static String espera(long eventoId) {
        return prefijo(eventoId) + "espera";
    }

    public static String habilitados(long eventoId) {
        return prefijo(eventoId) + "habilitados";
    }

    public static String secuencia(long eventoId) {
        return prefijo(eventoId) + "secuencia";
    }

    private static String prefijo(long eventoId) {
        return "fila:{" + eventoId + "}:";
    }

    private static List<String> claves(long eventoId) {
        return List.of(config(eventoId), espera(eventoId), habilitados(eventoId), secuencia(eventoId));
    }

    // ------------------------------------------------------------------ utilidades

    private static RedisScript<List> script(String nombre) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/" + nombre + ".lua")));
        script.setResultType(List.class);
        return script;
    }

    /** Elemento numerico del resultado: Redis devuelve enteros como Long y los puntajes como texto. */
    private static long entero(List<?> resultado, int indice) {
        Object valor = resultado.get(indice);
        if (valor instanceof Number n) {
            return n.longValue();
        }
        return numero(String.valueOf(valor));
    }

    private static long numero(String texto) {
        return (long) Double.parseDouble(texto);
    }
}
