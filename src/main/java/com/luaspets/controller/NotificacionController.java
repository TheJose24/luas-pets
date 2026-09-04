package com.luaspets.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.dto.NotificacionResponse;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.NotificacionService;

@Controller
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping("")
    public String listar(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long usuarioId = userDetails.getUsuario().getId();
        var notificaciones = notificacionService.listarTodas(usuarioId).stream()
                .map(NotificacionResponse::from)
                .toList();
        model.addAttribute("notificaciones", notificaciones);
        model.addAttribute("rolActual", userDetails.getUsuario().getRol().name().toLowerCase(Locale.ROOT));
        return "notificaciones/lista";
    }
}
