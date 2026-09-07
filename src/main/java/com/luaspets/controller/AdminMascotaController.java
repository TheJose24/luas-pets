package com.luaspets.controller;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.dto.MascotaAdminRow;
import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.EstadoMascota;
import com.luaspets.model.Mascota;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.util.ImagenUtil;
import com.luaspets.util.PaginacionUtil;

@Controller
@RequestMapping("/admin/mascotas")
public class AdminMascotaController {

    private static final int TAMANIO_PAGINA = 10;
    private static final String[] MESES = { "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct",
            "Nov", "Dic" };

    private final MascotaRepository mascotaRepository;
    private final CitaRepository citaRepository;

    public AdminMascotaController(MascotaRepository mascotaRepository, CitaRepository citaRepository) {
        this.mascotaRepository = mascotaRepository;
        this.citaRepository = citaRepository;
    }

    @GetMapping("")
    public String listar(
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String especie,
            @RequestParam(required = false) String raza,
            @RequestParam(required = false) EstadoMascota estado,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        String buscarNorm = normalizar(buscar);
        String especieNorm = normalizar(especie);
        String razaNorm = normalizar(raza);
        int paginaSolicitada = Math.max(page, 0);

        PageRequest pageRequest = PageRequest.of(paginaSolicitada, TAMANIO_PAGINA, Sort.by("nombre").ascending());
        Page<Mascota> resultado = mascotaRepository.buscarConFiltros(buscarNorm, especieNorm, razaNorm, estado,
                pageRequest);

        List<Long> ids = resultado.getContent().stream().map(Mascota::getId).toList();
        Map<Long, LocalDateTime> ultimaConsultaPorMascota = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] fila : citaRepository.findUltimaCitaPorMascotas(ids, EstadoCita.ATENDIDA)) {
                ultimaConsultaPorMascota.put((Long) fila[0], (LocalDateTime) fila[1]);
            }
        }

        List<MascotaAdminRow> filas = resultado.getContent().stream()
                .map(m -> mapearFila(m, ultimaConsultaPorMascota.get(m.getId())))
                .toList();

        model.addAttribute("filas", filas);
        model.addAttribute("page", resultado);
        model.addAttribute("numerosPagina", PaginacionUtil.numerosPagina(resultado));
        model.addAttribute("especies", mascotaRepository.findEspeciesDistintas());
        model.addAttribute("razas", mascotaRepository.findRazasDistintas());
        model.addAttribute("estados", EstadoMascota.values());
        model.addAttribute("buscar", buscarNorm);
        model.addAttribute("especieSel", especieNorm);
        model.addAttribute("razaSel", razaNorm);
        model.addAttribute("estadoSel", estado);
        model.addAttribute("hayFiltros", buscarNorm != null || especieNorm != null || razaNorm != null || estado != null);
        model.addAttribute("queryFiltros", construirQueryFiltros(buscarNorm, especieNorm, razaNorm, estado));

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) paginaSolicitada * TAMANIO_PAGINA + 1;
        long hasta = total == 0 ? 0 : desde + resultado.getNumberOfElements() - 1;
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "admin/mascotas/lista";
    }

    @PostMapping("/{id:[0-9]+}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam EstadoMascota nuevoEstado,
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String especie,
            @RequestParam(required = false) String raza,
            @RequestParam(required = false) EstadoMascota estado,
            @RequestParam(required = false) Integer page,
            RedirectAttributes redirectAttributes) {

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada con id: " + id));
        mascota.setEstado(nuevoEstado);
        mascotaRepository.save(mascota);

        redirectAttributes.addFlashAttribute("exito", "Estado actualizado correctamente.");
        if (StringUtils.hasText(buscar)) {
            redirectAttributes.addAttribute("buscar", buscar);
        }
        if (StringUtils.hasText(especie)) {
            redirectAttributes.addAttribute("especie", especie);
        }
        if (StringUtils.hasText(raza)) {
            redirectAttributes.addAttribute("raza", raza);
        }
        if (estado != null) {
            redirectAttributes.addAttribute("estado", estado.name());
        }
        if (page != null) {
            redirectAttributes.addAttribute("page", page);
        }

        return "redirect:/admin/mascotas";
    }

    private String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private String construirQueryFiltros(String buscar, String especie, String raza, EstadoMascota estado) {
        StringBuilder sb = new StringBuilder();
        if (buscar != null) {
            sb.append("&buscar=").append(URLEncoder.encode(buscar, StandardCharsets.UTF_8));
        }
        if (especie != null) {
            sb.append("&especie=").append(URLEncoder.encode(especie, StandardCharsets.UTF_8));
        }
        if (raza != null) {
            sb.append("&raza=").append(URLEncoder.encode(raza, StandardCharsets.UTF_8));
        }
        if (estado != null) {
            sb.append("&estado=").append(estado.name());
        }
        return sb.toString();
    }

    private MascotaAdminRow mapearFila(Mascota mascota, LocalDateTime ultimaConsulta) {
        EstadoMascota estado = mascota.getEstado() != null ? mascota.getEstado() : EstadoMascota.ACTIVO;

        return new MascotaAdminRow(
                mascota.getId(),
                String.format("#MP-%04d", mascota.getId()),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                resolverFoto(mascota.getEspecie(), mascota.getFotoUrl()),
                calcularEdadTexto(mascota.getFechaNacimiento()),
                formatearFecha(mascota.getFechaNacimiento()),
                formatearPeso(mascota.getPeso()),
                mascota.getCliente().getNombre() + " " + mascota.getCliente().getApellido(),
                StringUtils.hasText(mascota.getCliente().getTelefono()) ? mascota.getCliente().getTelefono() : "—",
                ultimaConsulta != null ? formatearFecha(ultimaConsulta.toLocalDate()) : "Sin consultas",
                estado,
                textoEstado(estado),
                varianteEstado(estado));
    }

    private String resolverFoto(String especie, String fotoUrl) {
        return ImagenUtil.resolverFoto(fotoUrl, especie);
    }

    private String calcularEdadTexto(LocalDate fechaNacimiento) {
        if (fechaNacimiento == null) {
            return "—";
        }
        Period periodo = Period.between(fechaNacimiento, LocalDate.now());
        if (periodo.getYears() == 0) {
            if (periodo.getMonths() == 0) {
                int dias = periodo.getDays();
                return dias + (dias == 1 ? " día" : " días");
            }
            return periodo.getMonths() + (periodo.getMonths() == 1 ? " mes" : " meses");
        }
        if (periodo.getYears() == 1) {
            return "1 año";
        }
        return periodo.getYears() + " años";
    }

    private String formatearFecha(LocalDate fecha) {
        if (fecha == null) {
            return "—";
        }
        return fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1] + " " + fecha.getYear();
    }

    private String formatearPeso(BigDecimal peso) {
        if (peso == null) {
            return "—";
        }
        return String.format(Locale.US, "%.2f kg", peso);
    }

    private String textoEstado(EstadoMascota estado) {
        return switch (estado) {
            case ACTIVO -> "Activo";
            case EN_TRATAMIENTO -> "En tratamiento";
            case INACTIVO -> "Inactivo";
        };
    }

    private String varianteEstado(EstadoMascota estado) {
        return switch (estado) {
            case ACTIVO -> "success";
            case EN_TRATAMIENTO -> "warning";
            case INACTIVO -> "secondary";
        };
    }
}
