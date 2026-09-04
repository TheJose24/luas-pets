package com.luaspets.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.dto.SemanaCalendario;
import com.luaspets.exception.BusinessException;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.HistorialMedico;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CalendarioService;
import com.luaspets.service.CitaService;
import com.luaspets.service.HistorialMedicoService;

@Controller
@RequestMapping("/doctor/citas")
public class DoctorCitaController {

    private final CitaService citaService;
    private final HistorialMedicoService historialMedicoService;
    private final CalendarioService calendarioService;

    public DoctorCitaController(CitaService citaService, HistorialMedicoService historialMedicoService,
            CalendarioService calendarioService) {
        this.citaService = citaService;
        this.historialMedicoService = historialMedicoService;
        this.calendarioService = calendarioService;
    }

    @GetMapping("")
    public String agenda(
            @RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "calendario") String vista,
            @RequestParam(required = false) String semana,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Long doctorId = userDetails.getUsuario().getId();
        List<Cita> propias = citaService.listarPorDoctor(doctorId);

        String buscarNorm = normalizar(buscar);

        List<Cita> citasFiltradas = propias.stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> buscarNorm == null || coincideBusqueda(c, buscarNorm))
                .toList();

        model.addAttribute("citas", citasFiltradas);
        model.addAttribute("vista", vista);

        if ("calendario".equals(vista)) {
            LocalDate lunes = resolverLunes(semana);
            // A diferencia del admin, el doctor si tiene destinos utiles: atender la
            // cita (si sigue activa) o ver la ficha del paciente (si ya se resolvio),
            // asi que cada tarjeta de su calendario es clicable.
            SemanaCalendario semanaCalendario = calendarioService.construirSemana(citasFiltradas, lunes,
                    cita -> (cita.getEstado() != EstadoCita.ATENDIDA && cita.getEstado() != EstadoCita.CANCELADA)
                            ? "/doctor/citas/" + cita.getId() + "/atender"
                            : "/doctor/pacientes/" + cita.getMascota().getId());
            model.addAttribute("semana", semanaCalendario);
        }

        model.addAttribute("estadoSel", estado);
        model.addAttribute("buscar", buscarNorm);
        model.addAttribute("queryFiltros", construirQueryFiltros(estado, buscarNorm));

        return "doctor/citas/agenda";
    }

    @GetMapping("/{id}/atender")
    public String atenderForm(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        Cita cita = citaService.buscarPorId(id);
        validarDoctor(cita, userDetails);
        model.addAttribute("cita", cita);
        model.addAttribute("historial", new HistorialMedico());
        return "doctor/citas/atender";
    }

    @PostMapping("/{id}/atender")
    public String atender(@PathVariable("id") Long citaId, @ModelAttribute HistorialMedico historial,
            @AuthenticationPrincipal CustomUserDetails userDetails, RedirectAttributes redirectAttributes) {
        Cita cita = citaService.buscarPorId(citaId);
        validarDoctor(cita, userDetails);

        try {
            historialMedicoService.registrarHistorial(historial, citaId);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/doctor/citas/" + citaId + "/atender";
        }
        return "redirect:/doctor/citas?atendida";
    }

    @PostMapping("/{id:[0-9]+}/confirmar")
    public String confirmar(@PathVariable("id") Long citaId, @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        Cita cita = citaService.buscarPorId(citaId);
        validarDoctor(cita, userDetails);

        try {
            citaService.confirmarCita(citaId);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/doctor/citas";
        }
        redirectAttributes.addFlashAttribute("exito", "Cita confirmada correctamente.");
        return "redirect:/doctor/citas";
    }

    private void validarDoctor(Cita cita, CustomUserDetails userDetails) {
        Long doctorId = userDetails.getUsuario().getId();
        if (!cita.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("La cita no pertenece al doctor logueado");
        }
    }

    private LocalDate resolverLunes(String semana) {
        if (semana != null && !semana.isBlank()) {
            try {
                return LocalDate.parse(semana).with(DayOfWeek.MONDAY);
            } catch (Exception e) {
                return LocalDate.now().with(DayOfWeek.MONDAY);
            }
        }
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private String normalizar(String valor) {
        return (valor != null && !valor.isBlank()) ? valor.trim() : null;
    }

    private boolean coincideBusqueda(Cita cita, String termino) {
        String t = termino.toLowerCase(Locale.ROOT);
        return contiene(cita.getMascota().getNombre(), t)
                || contiene(cita.getMascota().getCliente().getNombre(), t)
                || contiene(cita.getMascota().getCliente().getApellido(), t);
    }

    private boolean contiene(String valor, String termino) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(termino);
    }

    private String construirQueryFiltros(EstadoCita estado, String buscar) {
        StringBuilder sb = new StringBuilder();
        if (estado != null) {
            sb.append("&estado=").append(estado.name());
        }
        if (buscar != null) {
            sb.append("&buscar=").append(URLEncoder.encode(buscar, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
