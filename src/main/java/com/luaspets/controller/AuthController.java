package com.luaspets.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.luaspets.exception.BusinessException;
import com.luaspets.model.Usuario;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.UsuarioService;

@Controller
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            return switch (userDetails.getUsuario().getRol()) {
                case ADMIN -> "redirect:/admin/dashboard";
                case DOCTOR -> "redirect:/doctor/dashboard";
                case CLIENTE -> "redirect:/cliente/dashboard";
            };
        }
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/registro")
    public String registroForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@ModelAttribute Usuario usuario,
            @RequestParam(required = false) String confirmPassword, Model model) {
        if (usuario.getPassword() == null || usuario.getPassword().length() < 8) {
            model.addAttribute("error", "La contraseña debe tener al menos 8 caracteres");
            return "registro";
        }
        if (confirmPassword == null || confirmPassword.isEmpty() || !confirmPassword.equals(usuario.getPassword())) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            return "registro";
        }
        try {
            usuarioService.registrarCliente(usuario);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "registro";
        }
        return "redirect:/login?registrado";
    }
}
