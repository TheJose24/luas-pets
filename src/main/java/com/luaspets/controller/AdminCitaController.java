package com.luaspets.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.dto.SemanaCalendario;
import com.luaspets.exception.BusinessException;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.service.CalendarioService;
import com.luaspets.service.CitaService;
import com.luaspets.service.UsuarioService;

@Controller
@RequestMapping("/admin/citas")
public class AdminCitaController {

    private final CitaService citaService;
    private final UsuarioService usuarioService;
    private final CalendarioService calendarioService;

    public AdminCitaController(CitaService citaService, UsuarioService usuarioService,
            CalendarioService calendarioService) {
        this.citaService = citaService;
        this.usuarioService = usuarioService;
        this.calendarioService = calendarioService;
    }

    @GetMapping("")
    public String listar(
            @RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String especie,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "calendario") String vista,
            @RequestParam(required = false) String semana,
            Model model) {

        List<Cita> todas = citaService.listarTodas();

        String especieNorm = normalizar(especie);
        String buscarNorm = normalizar(buscar);

        List<Cita> citasFiltradas = todas.stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> doctorId == null || doctorId.equals(c.getDoctor().getId()))
                .filter(c -> especieNorm == null || especieNorm.equals(c.getMascota().getEspecie()))
                .filter(c -> buscarNorm == null || coincideBusqueda(c, buscarNorm))
                .toList();

        model.addAttribute("citas", citasFiltradas);
        model.addAttribute("vista", vista);

        if ("calendario".equals(vista)) {
            LocalDate lunes = resolverLunes(semana);
            // El admin no tiene un endpoint de detalle de cita propio (ese destino
            // es del cliente y del doctor, y ambos validan propiedad); por eso sus
            // tarjetas del calendario no llevan URL y la vista las muestra como
            // informacion de solo lectura, sin ser clicables.
            SemanaCalendario semanaCalendario = calendarioService.construirSemana(citasFiltradas, lunes, null);
            model.addAttribute("semana", semanaCalendario);
        }

        model.addAttribute("doctores", usuarioService.listarDoctoresActivos());
        model.addAttribute("especies", todas.stream()
                .map(c -> c.getMascota().getEspecie())
                .filter(e -> e != null && !e.isBlank())
                .distinct()
                .sorted()
                .toList());

        model.addAttribute("estadoSel", estado);
        model.addAttribute("doctorSel", doctorId);
        model.addAttribute("especieSel", especieNorm);
        model.addAttribute("buscar", buscarNorm);
        model.addAttribute("queryFiltros", construirQueryFiltros(estado, doctorId, especieNorm, buscarNorm));

        return "admin/citas/lista";
    }

    @PostMapping("/{id:[0-9]+}/confirmar")
    public String confirmar(@PathVariable("id") Long citaId,
            @RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String especie,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "calendario") String vista,
            @RequestParam(required = false) String semana,
            RedirectAttributes redirectAttributes) {
        try {
            citaService.confirmarCita(citaId);
            redirectAttributes.addFlashAttribute("exito", "Cita confirmada correctamente.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        String especieNorm = normalizar(especie);
        String buscarNorm = normalizar(buscar);
        StringBuilder query = new StringBuilder("vista=").append(vista);
        query.append(construirQueryFiltros(estado, doctorId, especieNorm, buscarNorm));
        if (semana != null && !semana.isBlank()) {
            query.append("&semana=").append(semana);
        }
        return "redirect:/admin/citas?" + query;
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
                || contiene(cita.getMascota().getCliente().getApellido(), t)
                || contiene(cita.getDoctor().getNombre(), t)
                || contiene(cita.getDoctor().getApellido(), t);
    }

    private boolean contiene(String valor, String termino) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(termino);
    }

    private String construirQueryFiltros(EstadoCita estado, Long doctorId, String especie, String buscar) {
        StringBuilder sb = new StringBuilder();
        if (estado != null) {
            sb.append("&estado=").append(estado.name());
        }
        if (doctorId != null) {
            sb.append("&doctorId=").append(doctorId);
        }
        if (especie != null) {
            sb.append("&especie=").append(URLEncoder.encode(especie, StandardCharsets.UTF_8));
        }
        if (buscar != null) {
            sb.append("&buscar=").append(URLEncoder.encode(buscar, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
