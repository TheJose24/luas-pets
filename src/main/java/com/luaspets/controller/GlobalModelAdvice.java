package com.luaspets.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.NotificacionService;

/**
 * Expone el conteo de notificaciones no leidas a todas las vistas Thymeleaf,
 * para que la campana del topbar lo muestre sin que cada controlador tenga
 * que agregarlo a su Model.
 *
 * NOTA sobre el filtrado: "@ControllerAdvice(annotations = Controller.class)"
 * NO excluye a los @RestController como pudiera parecer, porque @RestController
 * esta a su vez meta-anotado con @Controller y Spring resuelve esa herencia de
 * meta-anotaciones (AnnotationUtils.findAnnotation) al decidir si un advice
 * aplica a un bean; el propio ejemplo de la documentacion de Spring para
 * restringir a solo @RestController usa "annotations = RestController.class",
 * lo que confirma que "Controller.class" por si solo no discrimina entre
 * ambos. Spring no ofrece una forma de excluir por anotacion ("todo @Controller
 * menos @RestController"), asi que aqui se usa "assignableTypes" listando
 * explicitamente los @Controller de vistas Thymeleaf. Los @RestController del
 * proyecto (CarritoApiController, HealthController, NotificacionApiController)
 * quedan fuera a proposito: devuelven JSON, no usan Model, y por eso no deben
 * pagar la consulta de notificaciones no leidas en cada peticion.
 *
 * Si se agrega un nuevo @Controller de vistas, debe sumarse a esta lista.
 */
@ControllerAdvice(assignableTypes = {
        AdminCitaController.class,
        AdminDoctorController.class,
        AdminMascotaController.class,
        AdminPedidoController.class,
        AdminProductoController.class,
        AuthController.class,
        CarritoController.class,
        CitaController.class,
        DashboardController.class,
        DoctorCitaController.class,
        DoctorPacienteController.class,
        MascotaController.class,
        NotificacionController.class,
        PedidoController.class,
        TiendaController.class
})
public class GlobalModelAdvice {

    private final NotificacionService notificacionService;

    public GlobalModelAdvice(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @ModelAttribute("notificacionesNoLeidas")
    public long notificacionesNoLeidas(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return 0;
        }
        return notificacionService.contarNoLeidas(userDetails.getUsuario().getId());
    }
}
