package com.luaspets.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import com.luaspets.repository.CitaRepository;
import com.luaspets.service.CalendarioService;
import com.luaspets.service.CitaService;
import com.luaspets.service.UsuarioService;
import com.luaspets.util.PaginacionUtil;

@Controller
@RequestMapping("/admin/citas")
public class AdminCitaController {

    private static final int TAMANIO_PAGINA = 10;

    private final CitaService citaService;
    private final CitaRepository citaRepository;
    private final UsuarioService usuarioService;
    private final CalendarioService calendarioService;

    public AdminCitaController(CitaService citaService, CitaRepository citaRepository,
            UsuarioService usuarioService, CalendarioService calendarioService) {
        this.citaService = citaService;
        this.citaRepository = citaRepository;
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
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        String especieNorm = normalizar(especie);
        String buscarNorm = normalizar(buscar);

        model.addAttribute("vista", vista);

        if ("calendario".equals(vista)) {
            LocalDate lunes = calendarioService.resolverLunes(semana);
            LocalDate domingo = lunes.plusDays(6);
            LocalDateTime desdeRango = LocalDateTime.of(lunes, LocalTime.MIN);
            LocalDateTime hastaRango = LocalDateTime.of(domingo, LocalTime.of(23, 59, 59));

            // Acotado por la semana visible en vez de cargar todas las citas del
            // sistema (ver informe de la tarea de paginacion: esto reemplaza el
            // citaService.listarTodas() + filtrado en memoria que habia antes).
            List<Cita> citasSemana = citaRepository.buscarEnRangoConFiltros(desdeRango, hastaRango, estado,
                    doctorId, especieNorm, buscarNorm);

            // El admin no tiene un endpoint de detalle de cita propio (ese destino
            // es del cliente y del doctor, y ambos validan propiedad); por eso sus
            // tarjetas del calendario no llevan URL y la vista las muestra como
            // informacion de solo lectura, sin ser clicables.
            SemanaCalendario semanaCalendario = calendarioService.construirSemana(citasSemana, lunes, null);
            model.addAttribute("semana", semanaCalendario);
        } else {
            int paginaSolicitada = Math.max(page, 0);
            PageRequest pageRequest = PageRequest.of(paginaSolicitada, TAMANIO_PAGINA,
                    Sort.by("fechaHora").descending());
            Page<Cita> resultado = citaRepository.buscarTodasConFiltros(estado, doctorId, especieNorm, buscarNorm,
                    pageRequest);

            model.addAttribute("page", resultado);
            model.addAttribute("numerosPagina", PaginacionUtil.numerosPagina(resultado));
            model.addAttribute("citas", resultado.getContent());

            long total = resultado.getTotalElements();
            long desde = total == 0 ? 0 : (long) paginaSolicitada * TAMANIO_PAGINA + 1;
            long hasta = total == 0 ? 0 : desde + resultado.getNumberOfElements() - 1;
            model.addAttribute("desde", desde);
            model.addAttribute("hasta", hasta);
        }

        boolean hayFiltros = estado != null || doctorId != null || especieNorm != null || buscarNorm != null;

        model.addAttribute("doctores", usuarioService.listarDoctoresActivos());
        model.addAttribute("especies", citaRepository.findEspeciesDistintasEnCitas());
        model.addAttribute("estadoSel", estado);
        model.addAttribute("doctorSel", doctorId);
        model.addAttribute("especieSel", especieNorm);
        model.addAttribute("buscar", buscarNorm);
        model.addAttribute("hayFiltros", hayFiltros);
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
            @RequestParam(required = false) Integer page,
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
        if (page != null) {
            query.append("&page=").append(page);
        }
        return "redirect:/admin/citas?" + query;
    }

    private String normalizar(String valor) {
        return (valor != null && !valor.isBlank()) ? valor.trim() : null;
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
