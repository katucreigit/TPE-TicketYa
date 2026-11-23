package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.client.EventoCatalogo;
import com.ticketya.recomendaciones.model.Preferencias;
import com.ticketya.recomendaciones.model.TipoEvento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Puntaje de recomendacion como funcion pura (design.md D3), sin Spring ni base:
 * <pre>
 *   3 si el tipo esta en las preferencias                        (TIPO)
 * + 3 si algun participante coincide con un artista favorito      (ARTISTA)
 * + 2 si la ciudad coincide                                       (CIUDAD)
 * + 2 x compradoresEnComun / maximo de compradores en comun       (TAMBIEN_COMPRARON)
 * + 1 x compradores / maximo de compradores                       (POPULAR)
 * </pre>
 * Los maximos se toman entre los candidatos, asi que una preferencia explicita pesa mas que la popularidad.
 */
public final class CalculadoraPuntaje {

    public static final double PESO_TIPO = 3;
    public static final double PESO_ARTISTA = 3;
    public static final double PESO_CIUDAD = 2;
    public static final double PESO_TAMBIEN_COMPRARON = 2;
    public static final double PESO_POPULAR = 1;

    public enum Motivo { TIPO, ARTISTA, CIUDAD, TAMBIEN_COMPRARON, POPULAR }

    public record Puntuado(EventoCatalogo evento, double puntaje, List<Motivo> motivos) {
    }

    /** Orden de la respuesta: puntaje descendente, luego fecha mas proxima, luego id. */
    public static final Comparator<Puntuado> ORDEN = Comparator
            .comparingDouble(Puntuado::puntaje).reversed()
            .thenComparing(p -> p.evento().fechaHora(), Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(p -> p.evento().id());

    private final Set<String> tipos;
    private final Set<String> artistas;
    private final String ciudad;

    public CalculadoraPuntaje(Preferencias preferencias) {
        this.tipos = preferencias.getTipos().stream().map(TipoEvento::name).collect(Collectors.toSet());
        this.artistas = preferencias.getArtistas().stream().map(Texto::normalizar).collect(Collectors.toSet());
        this.ciudad = preferencias.getCiudad() == null ? null : Texto.normalizar(preferencias.getCiudad());
    }

    public Puntuado puntuar(EventoCatalogo evento, long compradores, long maxCompradores,
                            long enComun, long maxEnComun) {
        double puntaje = 0;
        List<Motivo> motivos = new ArrayList<>();
        if (evento.tipo() != null && tipos.contains(evento.tipo())) {
            puntaje += PESO_TIPO;
            motivos.add(Motivo.TIPO);
        }
        if (evento.participantesONada().stream().map(Texto::normalizar).anyMatch(artistas::contains)) {
            puntaje += PESO_ARTISTA;
            motivos.add(Motivo.ARTISTA);
        }
        if (ciudad != null && ciudad.equals(Texto.normalizar(evento.ciudad()))) {
            puntaje += PESO_CIUDAD;
            motivos.add(Motivo.CIUDAD);
        }
        if (enComun > 0 && maxEnComun > 0) {
            puntaje += PESO_TAMBIEN_COMPRARON * enComun / maxEnComun;
            motivos.add(Motivo.TAMBIEN_COMPRARON);
        }
        if (compradores > 0 && maxCompradores > 0) {
            puntaje += PESO_POPULAR * compradores / maxCompradores;
            motivos.add(Motivo.POPULAR);
        }
        return new Puntuado(evento, redondear(puntaje), List.copyOf(motivos));
    }

    static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
