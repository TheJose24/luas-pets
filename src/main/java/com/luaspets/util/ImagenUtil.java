package com.luaspets.util;

public final class ImagenUtil {

    public static final String FALLBACK_PERRO = "/images/default-perro.jpg";
    public static final String FALLBACK_GATO = "/images/default-gato.jpg";
    public static final String FALLBACK_OTRO = "/images/default-otro.jpg";

    private ImagenUtil() {
    }

    public static String resolverFoto(String fotoUrl, String especie) {
        if (fotoUrl != null && !fotoUrl.isBlank()) {
            return fotoUrl;
        }
        if (especie == null) {
            return FALLBACK_OTRO;
        }
        if ("perro".equalsIgnoreCase(especie)) {
            return FALLBACK_PERRO;
        }
        if ("gato".equalsIgnoreCase(especie)) {
            return FALLBACK_GATO;
        }
        return FALLBACK_OTRO;
    }
}
