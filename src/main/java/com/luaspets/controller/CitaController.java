package com.luaspets.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.exception.BusinessException;
import com.luaspets.model.Cita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CitaService;
import com.luaspets.service.MascotaService;
import com.luaspets.service.UsuarioService;

@Controller
@RequestMapping("/cliente/citas")
public class CitaController {

    private static final DateTimeFormatter DATETIME_LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final long HORAS_ANTICIPACION_MINIMA = 24;
    private static final long MESES_ANTICIPACION_MAXIMA = 6;

    private final CitaService citaService;
    private final MascotaService mascotaService;
    private final UsuarioService usuarioService;

    public CitaController(CitaService citaService, MascotaService mascotaService, UsuarioService usuarioService) {
        this.citaService = citaService;
        this.mascotaService = mascotaService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("")
    public String listar(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        model.addAttribute("citas", citaService.listarPorCliente(clienteId));
        return "cliente/citas/lista";
    }

    @GetMapping("/nueva")
    public String nuevaForm(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        model.addAttribute("mascotas", mascotaService.listarPorCliente(clienteId));
        model.addAttribute("doctores", usuarioService.listarDoctoresActivos());
        agregarLimitesDeFecha(model);
        return "cliente/citas/formulario";
    }

    @PostMapping("/nueva")
    public String nueva(@RequestParam Long mascotaId, @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHora,
            @RequestParam String motivo, @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        Long clienteId = userDetails.getUsuario().getId();
        Mascota mascota = mascotaService.buscarPorId(mascotaId);
        if (!mascota.getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException("La mascota no pertenece al cliente logueado");
        }
        Usuario doctor = usuarioService.buscarPorId(doctorId);
        if (doctor.getRol() != Rol.DOCTOR) {
            redirectAttributes.addFlashAttribute("error", "El doctor seleccionado no es válido");
            return "redirect:/cliente/citas/nueva";
        }

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaHora);
        cita.setMotivo(motivo);

        try {
            citaService.agendarCita(cita);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cliente/citas/nueva";
        }
        return "redirect:/cliente/citas?exito";
    }

    @GetMapping("/{id}/reprogramar")
    public String reprogramarForm(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        Cita cita = citaService.buscarPorId(id);
        validarPropietario(cita, userDetails);
        model.addAttribute("cita", cita);
        agregarLimitesDeFecha(model);
        return "cliente/citas/reprogramar";
    }

    @PostMapping("/{id}/reprogramar")
    public String reprogramar(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime nuevaFechaHora,
            @AuthenticationPrincipal CustomUserDetails userDetails, RedirectAttributes redirectAttributes) {
        Cita cita = citaService.buscarPorId(id);
        validarPropietario(cita, userDetails);

        try {
            citaService.reprogramarCita(id, nuevaFechaHora);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cliente/citas/" + id + "/reprogramar";
        }
        return "redirect:/cliente/citas?reprogramada";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        Cita cita = citaService.buscarPorId(id);
        validarPropietario(cita, userDetails);

        try {
            citaService.cancelarCita(id);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cliente/citas";
    }

    private void agregarLimitesDeFecha(Model model) {
        LocalDateTime ahora = LocalDateTime.now();
        model.addAttribute("minFechaHora", ahora.plusHours(HORAS_ANTICIPACION_MINIMA).format(DATETIME_LOCAL_FORMAT));
        model.addAttribute("maxFechaHora",
                ahora.plusMonths(MESES_ANTICIPACION_MAXIMA).format(DATETIME_LOCAL_FORMAT));
    }

    private void validarPropietario(Cita cita, CustomUserDetails userDetails) {
        Long clienteId = userDetails.getUsuario().getId();
        if (!cita.getMascota().getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException("La cita no pertenece al cliente logueado");
        }
    }
}
