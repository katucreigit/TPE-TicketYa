package com.ticketya.recomendaciones.service;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizacion para comparar artistas y ciudades: minusculas, sin tildes y sin espacios en los extremos (D3). */
public final class Texto {

    private Texto() {
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
