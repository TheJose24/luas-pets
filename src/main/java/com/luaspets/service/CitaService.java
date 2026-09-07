package com.luaspets.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.exception.BusinessException;
import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.Mascota;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.UsuarioRepository;

@Service
public class CitaService {

    private static final Logger log = LoggerFactory.getLogger(CitaService.class);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final LocalTime HORA_APERTURA = LocalTime.of(8, 0);
    private static final LocalTime HORA_CIERRE = LocalTime.of(20, 0);
    private static final long HORAS_ANTICIPACION_MINIMA = 24;

    private final CitaRepository citaRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public CitaService(CitaRepository citaRepository, MascotaRepository mascotaRepository,
            UsuarioRepository usuarioRepository, NotificacionService notificacionService) {
        this.citaRepository = citaRepository;
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public Cita agendarCita(Cita cita) {
        Mascota mascota = mascotaRepository.findById(cita.getMascota().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mascota no encontrada con id: " + cita.getMascota().getId()));
        Usuario doctor = usuarioRepository.findById(cita.getDoctor().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor no encontrado con id: " + cita.getDoctor().getId()));

        validarHorario(cita.getFechaHora());

        if (citaRepository.existsByDoctorIdAndFechaHoraAndEstadoNot(doctor.getId(), cita.getFechaHora(),
                EstadoCita.CANCELADA)) {
            throw new BusinessException("El doctor ya tiene una cita agendada en ese horario");
        }

        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setFechaCreacion(LocalDateTime.now());
        Cita guardada = citaRepository.save(cita);

        // La generacion de notificaciones nunca debe hacer fallar una operacion de
        // negocio ya completada: se envuelve en try/catch y solo se registra el
        // error, tal como en el resto de metodos de este servicio.
        try {
            notificacionService.crear(doctor, TipoNotificacion.CITA_AGENDADA, "Nueva cita agendada",
                    mascota.getNombre() + " tiene una cita el " + guardada.getFechaHora().format(FORMATO_FECHA)
                            + ". Motivo: " + guardada.getMotivo(),
                    "/doctor/citas");
        } catch (Exception e) {
            log.error("No se pudo notificar al doctor {} sobre la cita agendada {}", doctor.getId(),
                    guardada.getId(), e);
        }

        return guardada;
    }

    @Transactional
    public Cita reprogramarCita(Long citaId, LocalDateTime nuevaFechaHora) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.PENDIENTE && cita.getEstado() != EstadoCita.CONFIRMADA) {
            throw new BusinessException("Solo se pueden reprogramar citas pendientes o confirmadas");
        }
        validarHorario(nuevaFechaHora);
        boolean ocupado = citaRepository
                .findByDoctorIdAndFechaHoraAndEstadoNot(cita.getDoctor().getId(), nuevaFechaHora, EstadoCita.CANCELADA)
                .stream()
                .anyMatch(c -> !c.getId().equals(citaId));
        if (ocupado) {
            throw new BusinessException("El doctor ya tiene una cita agendada en ese horario");
        }
        cita.setFechaHora(nuevaFechaHora);
        Cita guardada = citaRepository.save(cita);

        try {
            notificacionService.crear(guardada.getDoctor(), TipoNotificacion.CITA_REPROGRAMADA, "Cita reprogramada",
                    "La cita de " + guardada.getMascota().getNombre() + " se movió al "
                            + guardada.getFechaHora().format(FORMATO_FECHA) + ".",
                    "/doctor/citas");
        } catch (Exception e) {
            log.error("No se pudo notificar al doctor sobre la reprogramacion de la cita {}", guardada.getId(), e);
        }

        return guardada;
    }

    @Transactional
    public Cita confirmarCita(Long citaId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new BusinessException("Solo se pueden confirmar citas pendientes");
        }
        cita.setEstado(EstadoCita.CONFIRMADA);
        Cita guardada = citaRepository.save(cita);

        try {
            notificacionService.crear(guardada.getMascota().getCliente(), TipoNotificacion.CITA_CONFIRMADA,
                    "Tu cita fue confirmada",
                    "La cita de " + guardada.getMascota().getNombre() + " del "
                            + guardada.getFechaHora().format(FORMATO_FECHA) + " ha sido confirmada.",
                    "/cliente/citas");
        } catch (Exception e) {
            log.error("No se pudo notificar al cliente sobre la confirmacion de la cita {}", guardada.getId(), e);
        }

        return guardada;
    }

    @Transactional
    public Cita cancelarCita(Long citaId) {
        Cita cita = buscarPorId(citaId);
        if (cita.getEstado() == EstadoCita.ATENDIDA) {
            throw new BusinessException("No se puede cancelar una cita ya atendida");
        }
        cita.setEstado(EstadoCita.CANCELADA);
        Cita guardada = citaRepository.save(cita);

        try {
            notificacionService.crear(guardada.getDoctor(), TipoNotificacion.CITA_CANCELADA, "Cita cancelada",
                    "Se canceló la cita de " + guardada.getMascota().getNombre() + " del "
                            + guardada.getFechaHora().format(FORMATO_FECHA) + ".",
                    "/doctor/citas");
        } catch (Exception e) {
            log.error("No se pudo notificar al doctor sobre la cancelacion de la cita {}", guardada.getId(), e);
        }

        return guardada;
    }

    @Transactional
    public Cita marcarComoAtendida(Long citaId) {
        Cita cita = buscarPorId(citaId);
        cita.setEstado(EstadoCita.ATENDIDA);
        return citaRepository.save(cita);
    }

    public List<Cita> listarPorCliente(Long clienteId) {
        return citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(clienteId);
    }

    public List<Cita> listarPorMascota(Long mascotaId) {
        return citaRepository.findByMascotaIdOrderByFechaHoraDesc(mascotaId);
    }

    public Cita buscarPorId(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con id: " + id));
    }

    // Se aplica solo al agendar y reprogramar (crear/mover una cita hacia un
    // horario nuevo). NO se aplica a confirmar, cancelar ni marcar como
    // atendida: esas operan sobre citas ya existentes, y pueden referirse a
    // fechas pasadas (por ejemplo, atender una cita de hace 10 minutos) sin
    // que eso sea un error.
    private void validarHorario(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            throw new BusinessException("Debes indicar la fecha y hora de la cita");
        }
        if (fechaHora.isBefore(LocalDateTime.now().plusHours(HORAS_ANTICIPACION_MINIMA))) {
            throw new BusinessException("Las citas deben agendarse con al menos 24 horas de anticipación");
        }
        LocalTime hora = fechaHora.toLocalTime();
        if (hora.isBefore(HORA_APERTURA) || hora.isAfter(HORA_CIERRE)) {
            throw new BusinessException("El horario de atención es de 08:00 a 20:00");
        }
    }
}
