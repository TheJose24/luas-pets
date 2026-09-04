package com.luaspets.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.exception.BusinessException;
import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.Cita;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.HistorialMedicoRepository;
import com.luaspets.repository.MascotaRepository;

@Service
public class HistorialMedicoService {

    private static final Logger log = LoggerFactory.getLogger(HistorialMedicoService.class);

    private final HistorialMedicoRepository historialMedicoRepository;
    private final CitaRepository citaRepository;
    private final CitaService citaService;
    private final MascotaRepository mascotaRepository;
    private final NotificacionService notificacionService;

    public HistorialMedicoService(HistorialMedicoRepository historialMedicoRepository,
            CitaRepository citaRepository, CitaService citaService, MascotaRepository mascotaRepository,
            NotificacionService notificacionService) {
        this.historialMedicoRepository = historialMedicoRepository;
        this.citaRepository = citaRepository;
        this.citaService = citaService;
        this.mascotaRepository = mascotaRepository;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public HistorialMedico registrarHistorial(HistorialMedico historial, Long citaId) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con id: " + citaId));

        if (historialMedicoRepository.findByCitaId(citaId).isPresent()) {
            throw new BusinessException("Esta cita ya tiene un historial médico registrado");
        }

        historial.setId(null);
        historial.setCita(cita);
        historial.setFecha(LocalDateTime.now());
        HistorialMedico guardado = historialMedicoRepository.save(historial);

        // Regla de negocio: el peso de la mascota (Mascota.peso) representa su peso
        // ACTUAL, no un historico. Si esta consulta registro un peso valido, se
        // propaga como el nuevo peso vigente de la mascota.
        if (historial.getPesoRegistrado() != null && historial.getPesoRegistrado().compareTo(BigDecimal.ZERO) > 0) {
            Mascota mascota = cita.getMascota();
            mascota.setPeso(historial.getPesoRegistrado());
            mascotaRepository.save(mascota);
        }

        citaService.marcarComoAtendida(citaId);

        try {
            Mascota mascotaCita = cita.getMascota();
            notificacionService.crear(mascotaCita.getCliente(), TipoNotificacion.CITA_ATENDIDA,
                    "Historial médico disponible",
                    mascotaCita.getNombre() + " fue atendida por Dr. " + cita.getDoctor().getNombre()
                            + ". Ya puedes consultar el diagnóstico.",
                    "/cliente/mascotas/" + mascotaCita.getId());
        } catch (Exception e) {
            log.error("No se pudo notificar al cliente sobre el historial registrado en la cita {}", citaId, e);
        }

        return guardado;
    }

    public List<HistorialMedico> listarPorMascota(Long mascotaId) {
        return historialMedicoRepository.findByCitaMascotaIdOrderByFechaDesc(mascotaId);
    }
}
