package com.luaspets.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.exception.BusinessException;
import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.service.UsuarioService;

@Controller
@RequestMapping("/admin/doctores")
public class AdminDoctorController {

    private final UsuarioService usuarioService;

    public AdminDoctorController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("")
    public String listar(Model model) {
        model.addAttribute("doctores", usuarioService.listarPorRol(Rol.DOCTOR));
        return "admin/doctores/lista";
    }

    @GetMapping("/nuevo")
    public String nuevoForm(Model model) {
        model.addAttribute("doctor", new Usuario());
        return "admin/doctores/formulario";
    }

    @PostMapping("/nuevo")
    public String nuevo(@ModelAttribute Usuario doctor, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.crearDoctor(doctor);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/doctores/nuevo";
        }
        return "redirect:/admin/doctores?exito";
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(@PathVariable Long id) {
        usuarioService.desactivarUsuario(id);
        return "redirect:/admin/doctores";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id) {
        usuarioService.activarUsuario(id);
        return "redirect:/admin/doctores";
    }
}
