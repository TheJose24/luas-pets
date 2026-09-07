package com.luaspets.controller;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.luaspets.dto.NotificacionResponse;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.NotificacionService;
import com.luaspets.util.PaginacionUtil;

@Controller
@RequestMapping("/notificaciones")
public class NotificacionController {

    private static final int TAMANIO_PAGINA = 10;

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping("")
    public String listar(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page, Model model) {
        Long usuarioId = userDetails.getUsuario().getId();
        int paginaSolicitada = Math.max(page, 0);

        // Sin Sort explicito: el orden ya viene fijado por el nombre del metodo del
        // repositorio (OrderByFechaCreacionDesc).
        PageRequest pageRequest = PageRequest.of(paginaSolicitada, TAMANIO_PAGINA);
        Page<NotificacionResponse> resultado = notificacionService.listarTodasPaginado(usuarioId, pageRequest)
                .map(NotificacionResponse::from);

        model.addAttribute("notificaciones", resultado.getContent());
        model.addAttribute("page", resultado);
        model.addAttribute("numerosPagina", PaginacionUtil.numerosPagina(resultado));
        model.addAttribute("rolActual", userDetails.getUsuario().getRol().name().toLowerCase(Locale.ROOT));

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) paginaSolicitada * TAMANIO_PAGINA + 1;
        long hasta = total == 0 ? 0 : desde + resultado.getNumberOfElements() - 1;
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "notificaciones/lista";
    }
}
