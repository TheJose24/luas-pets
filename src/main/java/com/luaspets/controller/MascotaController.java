package com.luaspets.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.model.Cita;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CitaService;
import com.luaspets.service.ExpedienteService;
import com.luaspets.service.HistorialMedicoService;
import com.luaspets.service.MascotaService;

@Controller
@RequestMapping("/cliente/mascotas")
public class MascotaController {

    private final MascotaService mascotaService;
    private final CitaService citaService;
    private final HistorialMedicoService historialMedicoService;
    private final ExpedienteService expedienteService;

    public MascotaController(MascotaService mascotaService, CitaService citaService,
            HistorialMedicoService historialMedicoService, ExpedienteService expedienteService) {
        this.mascotaService = mascotaService;
        this.citaService = citaService;
        this.historialMedicoService = historialMedicoService;
        this.expedienteService = expedienteService;
    }

    @GetMapping("")
    public String listar(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        model.addAttribute("mascotas", mascotaService.listarPorCliente(clienteId));
        return "cliente/mascotas/lista";
    }

    @GetMapping("/nueva")
    public String nuevaForm(Model model) {
        model.addAttribute("mascota", new Mascota());
        return "cliente/mascotas/formulario";
    }

    @PostMapping("/nueva")
    public String nueva(@ModelAttribute Mascota mascota, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long clienteId = userDetails.getUsuario().getId();
        mascotaService.registrarMascota(mascota, clienteId);
        return "redirect:/cliente/mascotas?exito";
    }

    @GetMapping("/{id:[0-9]+}")
    public String perfil(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        Mascota mascota = mascotaService.buscarPorIdConCliente(id);
        if (!mascota.getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException("La mascota no pertenece al cliente logueado");
        }

        List<Cita> citas = citaService.listarPorMascota(id);
        List<HistorialMedico> historiales = historialMedicoService.listarPorMascota(id);

        model.addAttribute("expediente", expedienteService.construir(mascota, citas, historiales));
        model.addAttribute("consultas", expedienteService.construirConsultas(historiales));
        model.addAttribute("citas", citas);
        return "cliente/mascotas/perfil";
    }
}
