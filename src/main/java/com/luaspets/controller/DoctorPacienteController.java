package com.luaspets.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.model.Cita;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.repository.CitaRepository;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CitaService;
import com.luaspets.service.ExpedienteService;
import com.luaspets.service.HistorialMedicoService;
import com.luaspets.service.MascotaService;

@Controller
@RequestMapping("/doctor/pacientes")
public class DoctorPacienteController {

    private final MascotaService mascotaService;
    private final CitaService citaService;
    private final HistorialMedicoService historialMedicoService;
    private final CitaRepository citaRepository;
    private final ExpedienteService expedienteService;

    public DoctorPacienteController(MascotaService mascotaService, CitaService citaService,
            HistorialMedicoService historialMedicoService, CitaRepository citaRepository,
            ExpedienteService expedienteService) {
        this.mascotaService = mascotaService;
        this.citaService = citaService;
        this.historialMedicoService = historialMedicoService;
        this.citaRepository = citaRepository;
        this.expedienteService = expedienteService;
    }

    @GetMapping("/{id:[0-9]+}")
    public String ficha(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long doctorId = userDetails.getUsuario().getId();
        Mascota mascota = mascotaService.buscarPorIdConCliente(id);

        if (!citaRepository.existsByDoctorIdAndMascotaId(doctorId, id)) {
            throw new AccessDeniedException("El doctor nunca ha atendido a esta mascota");
        }

        List<Cita> citas = citaService.listarPorMascota(id);
        List<HistorialMedico> historiales = historialMedicoService.listarPorMascota(id);

        model.addAttribute("expediente", expedienteService.construir(mascota, citas, historiales));
        model.addAttribute("consultas", expedienteService.construirConsultas(historiales));
        model.addAttribute("citas", citas);
        return "doctor/pacientes/ficha";
    }
}
