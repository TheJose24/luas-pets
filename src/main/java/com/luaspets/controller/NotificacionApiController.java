package com.luaspets.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luaspets.dto.NotificacionListaResponse;
import com.luaspets.dto.NotificacionResponse;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.NotificacionService;

/**
 * API JSON de notificaciones, usada por la campana del topbar para cargar y
 * marcar notificaciones sin recargar la pagina.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionApiController {

    private final NotificacionService notificacionService;

    public NotificacionApiController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping("")
    public NotificacionListaResponse listar(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long usuarioId = userDetails.getUsuario().getId();
        var items = notificacionService.listarRecientes(usuarioId).stream()
                .map(NotificacionResponse::from)
                .toList();
        return new NotificacionListaResponse(items, notificacionService.contarNoLeidas(usuarioId));
    }

    @PostMapping("/{id:[0-9]+}/leer")
    public long leer(@PathVariable("id") Long notificacionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long usuarioId = userDetails.getUsuario().getId();
        notificacionService.marcarComoLeida(notificacionId, usuarioId);
        return notificacionService.contarNoLeidas(usuarioId);
    }

    @PostMapping("/leer-todas")
    public long leerTodas(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long usuarioId = userDetails.getUsuario().getId();
        notificacionService.marcarTodasComoLeidas(usuarioId);
        return notificacionService.contarNoLeidas(usuarioId);
    }
}
