package com.luaspets.controller;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.dto.ConsultaExpediente;
import com.luaspets.dto.ExpedienteMascota;
import com.luaspets.model.Cita;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CitaService;
import com.luaspets.service.ExpedientePdfService;
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
    private final ExpedientePdfService expedientePdfService;

    public MascotaController(MascotaService mascotaService, CitaService citaService,
            HistorialMedicoService historialMedicoService, ExpedienteService expedienteService,
            ExpedientePdfService expedientePdfService) {
        this.mascotaService = mascotaService;
        this.citaService = citaService;
        this.historialMedicoService = historialMedicoService;
        this.expedienteService = expedienteService;
        this.expedientePdfService = expedientePdfService;
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
        Mascota mascota = buscarMascotaDelClienteLogueado(id, userDetails);

        List<Cita> citas = citaService.listarPorMascota(id);
        List<HistorialMedico> historiales = historialMedicoService.listarPorMascota(id);

        model.addAttribute("expediente", expedienteService.construir(mascota, citas, historiales));
        model.addAttribute("consultas", expedienteService.construirConsultas(historiales));
        model.addAttribute("citas", citas);
        return "cliente/mascotas/perfil";
    }

    @GetMapping("/{id:[0-9]+}/expediente.pdf")
    public ResponseEntity<byte[]> expedientePdf(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Mascota mascota = buscarMascotaDelClienteLogueado(id, userDetails);

        List<Cita> citas = citaService.listarPorMascota(id);
        List<HistorialMedico> historiales = historialMedicoService.listarPorMascota(id);
        ExpedienteMascota expediente = expedienteService.construir(mascota, citas, historiales);
        List<ConsultaExpediente> consultas = expedienteService.construirConsultas(historiales);

        // El propio cliente ya conoce sus datos de contacto: se omiten para no
        // ser redundantes (a diferencia de la version que descarga el doctor).
        byte[] pdf = expedientePdfService.generar(expediente, consultas, false);

        return construirRespuestaPdf(pdf, expediente);
    }

    // Misma validacion de propiedad que usa el endpoint de vista, extraida
    // aqui para que el endpoint de descarga del PDF nunca pueda tener permisos
    // distintos (y por lo tanto mas laxos) que la vista equivalente.
    private Mascota buscarMascotaDelClienteLogueado(Long id, CustomUserDetails userDetails) {
        Long clienteId = userDetails.getUsuario().getId();
        Mascota mascota = mascotaService.buscarPorIdConCliente(id);
        if (!mascota.getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException("La mascota no pertenece al cliente logueado");
        }
        return mascota;
    }

    private ResponseEntity<byte[]> construirRespuestaPdf(byte[] pdf, ExpedienteMascota expediente) {
        String codigoSinSimbolo = expediente.getCodigo() == null ? ""
                : expediente.getCodigo().replace("#", "");
        String nombreArchivo = "expediente-" + sanear(expediente.getNombre()) + "-" + codigoSinSimbolo + ".pdf";

        ContentDisposition disposition = ContentDisposition.attachment().filename(nombreArchivo).build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(pdf.length)
                .body(pdf);
    }

    private String sanear(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
