package com.luaspets.controller;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.exception.BusinessException;
import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private static final String[] MESES_LARGO = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre" };

    private final UsuarioService usuarioService;

    public PerfilController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("")
    public String ver(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        // Se recarga desde la base de datos y no se usa directamente el usuario
        // de la sesion: si el usuario acaba de editar su perfil en otra pestaña,
        // o si esta es la primera carga tras el POST, el objeto de la sesion
        // podria no reflejar el estado mas reciente.
        Usuario usuario = usuarioService.buscarPorId(userDetails.getUsuario().getId());

        model.addAttribute("usuario", usuario);
        model.addAttribute("rolActual", usuario.getRol().name().toLowerCase(Locale.ROOT));
        model.addAttribute("rolTexto", textoRol(usuario.getRol()));
        model.addAttribute("fechaRegistroTexto", formatearFecha(usuario.getFechaRegistro()));
        return "perfil/ver";
    }

    @PostMapping("/datos")
    public String actualizarDatos(@RequestParam String nombre, @RequestParam String apellido,
            @RequestParam(required = false) String telefono, @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        try {
            Usuario actualizado = usuarioService.actualizarPerfil(userDetails.getUsuario().getId(), nombre, apellido,
                    telefono);
            actualizarAutenticacionEnSesion(actualizado, request, response);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/perfil";
        }
        redirectAttributes.addFlashAttribute("exito", "Tus datos se actualizaron correctamente.");
        return "redirect:/perfil";
    }

    @PostMapping("/password")
    public String actualizarPassword(@RequestParam String passwordActual, @RequestParam String passwordNueva,
            @RequestParam String passwordConfirmacion, @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        try {
            usuarioService.cambiarPassword(userDetails.getUsuario().getId(), passwordActual, passwordNueva,
                    passwordConfirmacion);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorPassword", e.getMessage());
            return "redirect:/perfil";
        }
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return "redirect:/login?reauth";
    }

    // Tras actualizar nombre/apellido, el CustomUserDetails que vive en el
    // SecurityContext de la sesion actual sigue siendo el que se creo al
    // iniciar sesion (con los valores viejos): Spring Security no lo vuelve a
    // consultar en cada peticion. Sin este reemplazo, el saludo del topbar y
    // de los dashboards seguiria mostrando el nombre anterior hasta que el
    // usuario cierre sesion y vuelva a entrar. Se reconstruye el principal con
    // el Usuario ya actualizado y se reemplaza el Authentication del
    // SecurityContext. HttpSessionSecurityContextRepository guarda explicitamente
    // el contexto actualizado para conservar el cambio en las siguientes
    // peticiones, como requiere Spring Security 7.
    private void actualizarAutenticacionEnSesion(Usuario actualizado, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication autenticacionActual = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacionActual.getPrincipal() instanceof CustomUserDetails old
                && old.getSecurityVersion() != actualizado.getSecurityVersion()) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            return;
        }
        CustomUserDetails nuevoPrincipal = new CustomUserDetails(actualizado);
        Authentication nuevaAutenticacion = new UsernamePasswordAuthenticationToken(
                nuevoPrincipal, autenticacionActual.getCredentials(), nuevoPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(nuevaAutenticacion);
        new HttpSessionSecurityContextRepository().saveContext(SecurityContextHolder.getContext(), request, response);
    }

    private String textoRol(Rol rol) {
        return switch (rol) {
            case CLIENTE -> "Cliente";
            case DOCTOR -> "Veterinario";
            case ADMIN -> "Administrador";
        };
    }

    private String formatearFecha(LocalDateTime fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.getDayOfMonth() + " de " + MESES_LARGO[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }
}
