package com.ticketya.recomendaciones.service;

import com.ticketya.recomendaciones.dto.PreferenciasRequest;
import com.ticketya.recomendaciones.dto.PreferenciasResponse;
import com.ticketya.recomendaciones.model.Preferencias;
import com.ticketya.recomendaciones.model.TipoEvento;
import com.ticketya.recomendaciones.repository.PreferenciasRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Guarda y lee las preferencias del cliente (design.md D2). Guardar reemplaza el documento completo. */
@Service
public class PreferenciasService {

    private final PreferenciasRepository repository;
    private final Clock clock;

    public PreferenciasService(PreferenciasRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PreferenciasResponse guardar(String clienteId, PreferenciasRequest request) {
        Preferencias p = new Preferencias(clienteId,
                tiposSinRepetir(request.tipos()),
                artistasSinRepetir(request.artistas()),
                ciudad(request.ciudad()),
                // MongoDB guarda milisegundos: asi la respuesta del PUT coincide con la del GET.
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        return PreferenciasResponse.de(repository.save(p));
    }

    public PreferenciasResponse obtener(String clienteId) {
        return repository.findById(clienteId)
                .map(PreferenciasResponse::de)
                .orElseGet(() -> PreferenciasResponse.vacias(clienteId));
    }

    /** Las preferencias guardadas, o vacias si el cliente nunca las guardo. */
    public Preferencias buscar(String clienteId) {
        Optional<Preferencias> p = repository.findById(clienteId);
        return p.orElseGet(() -> new Preferencias(clienteId, new ArrayList<>(), new ArrayList<>(), null, null));
    }

    private static List<TipoEvento> tiposSinRepetir(List<TipoEvento> tipos) {
        return tipos == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(tipos));
    }

    /** Sin repetidos segun la forma normalizada; se conserva la primera forma escrita, sin espacios extremos. */
    private static List<String> artistasSinRepetir(List<String> artistas) {
        if (artistas == null) {
            return new ArrayList<>();
        }
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String a : artistas) {
            unicos.putIfAbsent(Texto.normalizar(a), a.trim());
        }
        return new ArrayList<>(unicos.values());
    }

    private static String ciudad(String ciudad) {
        return ciudad == null || ciudad.isBlank() ? null : ciudad.trim();
    }
}
