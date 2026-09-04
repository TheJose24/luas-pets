package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import com.luaspets.controller.GlobalModelAdvice;

/**
 * GlobalModelAdvice.java NO usa "@ControllerAdvice(annotations = Controller.class)"
 * porque eso tambien captura a los @RestController (estan meta-anotados con
 * @Controller, y Spring resuelve esa herencia al decidir si un advice aplica
 * a un bean). Por eso usa "assignableTypes" con una lista explicita de los
 * @Controller de vistas Thymeleaf del proyecto.
 *
 * Esa lista es una fuente de verdad mantenida a mano, y por lo tanto fragil:
 * si alguien agrega un nuevo @Controller de vistas y olvida sumarlo a
 * assignableTypes, NO hay ningun error ni excepcion en tiempo de ejecucion.
 * Simplemente el atributo "notificacionesNoLeidas" nunca llega al Model de
 * ese controlador, y la campana de notificaciones no muestra el contador en
 * sus vistas — un bug silencioso, dificil de notar en revision de codigo y
 * facil de no detectar manualmente probando la app.
 *
 * Esta prueba es la red de seguridad: en cada ejecucion de la suite, escanea
 * el contexto real de Spring en busca de todo bean @Controller (sin
 * @RestController) del paquete com.luaspets.controller, y falla con un
 * mensaje explicito si alguno no esta en GlobalModelAdvice.assignableTypes.
 * Tambien falla en el sentido contrario: si la lista contiene una clase que
 * ya no es un @Controller de vistas valido (por ejemplo, si en el futuro se
 * convirtiera en @RestController).
 */
@SpringBootTest
@ActiveProfiles("test")
class GlobalModelAdviceCoberturaTest {

    private static final String PAQUETE_CONTROLADORES = "com.luaspets.controller";

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void todoControllerDeVistasEstaEnAssignableTypesDeGlobalModelAdvice() {
        Set<Class<?>> controladoresDeVistas = obtenerControladoresDeVistasDelContexto();
        Set<Class<?>> assignableTypes = leerAssignableTypes();

        Set<Class<?>> faltantes = new HashSet<>(controladoresDeVistas);
        faltantes.removeAll(assignableTypes);

        assertThat(faltantes)
                .withFailMessage(() -> construirMensajeFaltantes(faltantes))
                .isEmpty();
    }

    @Test
    void assignableTypesNoContieneRestControllersNiClasesQueYaNoSonControllersDeVistas() {
        Set<Class<?>> controladoresDeVistas = obtenerControladoresDeVistasDelContexto();
        Set<Class<?>> assignableTypes = leerAssignableTypes();

        Set<Class<?>> sobrantes = new HashSet<>(assignableTypes);
        sobrantes.removeAll(controladoresDeVistas);

        assertThat(sobrantes)
                .withFailMessage(() -> "GlobalModelAdvice.assignableTypes contiene "
                        + sobrantes.size() + " clase(s) que ya NO son un @Controller de vistas valido "
                        + "(o dejaron de existir como tal): " + nombresSimples(sobrantes) + ". "
                        + "Si alguna de estas ahora es un @RestController, o el controlador ya no existe, "
                        + "quitala de assignableTypes en GlobalModelAdvice — mantenerla ahi no rompe nada "
                        + "en tiempo de ejecucion, pero es ruido que puede confundir a quien lea la lista.")
                .isEmpty();
    }

    private Set<Class<?>> obtenerControladoresDeVistasDelContexto() {
        Set<Class<?>> resultado = new HashSet<>();
        applicationContext.getBeansWithAnnotation(Controller.class).values().forEach(bean -> {
            // Los beans pueden llegar envueltos en un proxy (CGLIB/JDK dynamic
            // proxy); AopUtils.getTargetClass() devuelve la clase real detras
            // del proxy, que es la que realmente lleva las anotaciones que
            // nos interesan.
            Class<?> claseReal = AopUtils.getTargetClass(bean);

            if (!claseReal.getName().startsWith(PAQUETE_CONTROLADORES)) {
                // Excluye controladores internos de Spring Boot (por ejemplo
                // BasicErrorController, que tambien es @Controller pero no es
                // una vista Thymeleaf de este proyecto y nunca deberia estar
                // en assignableTypes).
                return;
            }
            if (claseReal.isAnnotationPresent(RestController.class)) {
                // getBeansWithAnnotation(Controller.class) tambien devuelve los
                // @RestController, porque @RestController esta meta-anotado
                // con @Controller. Se descartan aqui explicitamente: son API
                // JSON y no deben estar en assignableTypes.
                return;
            }
            resultado.add(claseReal);
        });
        return resultado;
    }

    private Set<Class<?>> leerAssignableTypes() {
        ControllerAdvice anotacion = GlobalModelAdvice.class.getAnnotation(ControllerAdvice.class);
        assertThat(anotacion)
                .withFailMessage("GlobalModelAdvice ya no tiene @ControllerAdvice; esta prueba no puede leer "
                        + "assignableTypes. Si cambiaste el mecanismo de filtrado, actualiza esta prueba.")
                .isNotNull();
        return new HashSet<>(Arrays.asList(anotacion.assignableTypes()));
    }

    private String construirMensajeFaltantes(Set<Class<?>> faltantes) {
        StringBuilder mensaje = new StringBuilder();
        mensaje.append(faltantes.size())
                .append(" controlador(es) de vistas no estan en GlobalModelAdvice.assignableTypes: ")
                .append(nombresSimples(faltantes))
                .append(". Sin esto, el atributo \"notificacionesNoLeidas\" no llega al Model de esas vistas "
                        + "y la campana del topbar no mostrara el contador de notificaciones ahi, sin ningun "
                        + "error visible. Agrega la(s) clase(s) faltante(s) a la lista assignableTypes de "
                        + "@ControllerAdvice en com.luaspets.controller.GlobalModelAdvice.");
        return mensaje.toString();
    }

    private String nombresSimples(Set<Class<?>> clases) {
        return clases.stream().map(Class::getSimpleName).sorted().reduce((a, b) -> a + ", " + b).orElse("(ninguno)");
    }
}
