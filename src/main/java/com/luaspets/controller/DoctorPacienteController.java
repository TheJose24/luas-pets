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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.dto.ConsultaExpediente;
import com.luaspets.dto.ExpedienteMascota;
import com.luaspets.model.Cita;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.repository.CitaRepository;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CitaService;
import com.luaspets.service.ExpedientePdfService;
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
    private final ExpedientePdfService expedientePdfService;

    public DoctorPacienteController(MascotaService mascotaService, CitaService citaService,
            HistorialMedicoService historialMedicoService, CitaRepository citaRepository,
            ExpedienteService expedienteService, ExpedientePdfService expedientePdfService) {
        this.mascotaService = mascotaService;
        this.citaService = citaService;
        this.historialMedicoService = historialMedicoService;
        this.citaRepository = citaRepository;
        this.expedienteService = expedienteService;
        this.expedientePdfService = expedientePdfService;
    }

    @GetMapping("/{id:[0-9]+}")
    public String ficha(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Mascota mascota = buscarPacienteAtendidoPorElDoctor(id, userDetails);

        List<Cita> citas = citaService.listarPorMascota(id);
        List<HistorialMedico> historiales = historialMedicoService.listarPorMascota(id);

        model.addAttribute("expediente", expedienteService.construir(mascota, citas, historiales));
        model.addAttribute("consultas", expedienteService.construirConsultas(historiales));
        model.addAttribute("citas", citas);
        return "doctor/pacientes/ficha";
    }

    @GetMapping("/{id:[0-9]+}/expediente.pdf")
    public ResponseEntity<byte[]> expedientePdf(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Mascota mascota = buscarPacienteAtendidoPorElDoctor(id, userDetails);

        List<Cita> citas = citaService.listarPorMascota(id);
        List<HistorialMedico> historiales = historialMedicoService.listarPorMascota(id);
        ExpedienteMascota expediente = expedienteService.construir(mascota, citas, historiales);
        List<ConsultaExpediente> consultas = expedienteService.construirConsultas(historiales);

        // El doctor si necesita los datos de contacto del propietario (a
        // diferencia del propio cliente, que ya los conoce).
        byte[] pdf = expedientePdfService.generar(expediente, consultas, true);

        return construirRespuestaPdf(pdf, expediente);
    }

    // Misma validacion de autorizacion que usa el endpoint de vista, extraida
    // aqui para que el endpoint de descarga del PDF nunca pueda tener permisos
    // distintos (y por lo tanto mas laxos) que la vista equivalente.
    private Mascota buscarPacienteAtendidoPorElDoctor(Long id, CustomUserDetails userDetails) {
        Long doctorId = userDetails.getUsuario().getId();
        Mascota mascota = mascotaService.buscarPorIdConCliente(id);

        if (!citaRepository.existsByDoctorIdAndMascotaId(doctorId, id)) {
            throw new AccessDeniedException("El doctor nunca ha atendido a esta mascota");
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
