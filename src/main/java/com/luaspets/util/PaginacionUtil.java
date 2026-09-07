package com.luaspets.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Calcula, a partir de un objeto Page, la lista de numeros de pagina a
 * mostrar en los controles de paginacion (fragments/paginacion.html),
 * centrada en la pagina actual y limitada a un maximo de
 * MAX_NUMEROS_VISIBLES para no romper el diseno cuando hay muchas paginas.
 *
 * Se llama desde cada controlador (no desde la plantilla): usar el operador
 * T() de SpringEL en una plantilla para invocar este calculo esta prohibido
 * en este proyecto, porque una expresion asi puede compilar sin problema con
 * `mvn clean compile` y solo fallar al renderizar la vista en produccion (ya
 * paso una vez). Todo el calculo vive aqui, en codigo Java normal, cubierto
 * por el compilador y por tests.
 *
 * Representacion de los puntos suspensivos: se usa el valor centinela
 * SEPARADOR (-1) dentro de la lista de enteros, en vez de un DTO con un
 * numero y un indicador de "es separador". Se prefirio el centinela porque
 * un numero de pagina real de Spring Data (Page.getNumber()) nunca es
 * negativo, asi que -1 no puede confundirse jamas con una pagina valida; la
 * plantilla queda mas simple iterando una List<Integer> con un solo
 * th:if/th:unless por elemento, en vez de tener que acceder a una propiedad
 * extra (item.separador) en cada iteracion.
 */
public final class PaginacionUtil {

    private static final int MAX_NUMEROS_VISIBLES = 7;

    public static final int SEPARADOR = -1;

    private PaginacionUtil() {
    }

    public static List<Integer> numerosPagina(Page<?> page) {
        return numerosPagina(page.getNumber(), page.getTotalPages());
    }

    public static List<Integer> numerosPagina(int paginaActual, int totalPaginas) {
        List<Integer> numeros = new ArrayList<>();
        if (totalPaginas <= 0) {
            return numeros;
        }

        int[] rango = calcularRango(paginaActual, totalPaginas);
        int inicio = rango[0];
        int fin = rango[1];

        if (inicio > 0) {
            numeros.add(SEPARADOR);
        }
        for (int numero = inicio; numero <= fin; numero++) {
            numeros.add(numero);
        }
        if (fin < totalPaginas - 1) {
            numeros.add(SEPARADOR);
        }
        return numeros;
    }

    private static int[] calcularRango(int paginaActual, int totalPaginas) {
        if (totalPaginas <= MAX_NUMEROS_VISIBLES) {
            return new int[] { 0, Math.max(totalPaginas - 1, 0) };
        }
        int mitad = MAX_NUMEROS_VISIBLES / 2;
        int inicio = paginaActual - mitad;
        int fin = paginaActual + mitad;
        if (inicio < 0) {
            fin += -inicio;
            inicio = 0;
        }
        if (fin > totalPaginas - 1) {
            inicio -= fin - (totalPaginas - 1);
            fin = totalPaginas - 1;
        }
        inicio = Math.max(inicio, 0);
        return new int[] { inicio, fin };
    }
}
