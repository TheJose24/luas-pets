package com.luaspets.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.Mascota;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;

/**
 * Genera recordatorios (notificaciones internas, nunca correos) para las
 * citas del dia siguiente, tanto al cliente dueno de la mascota como al
 * doctor asignado. El control de duplicados consulta la propia tabla de
 * notificaciones (destinatario + tipo + url) en cada ejecucion, en vez de
 * marcar la entidad Cita: es robusto ante reinicios de la aplicacion, porque
 * no depende de un estado en memoria ni de una bandera que pudiera quedar a
 * medio actualizar si el proceso se cae a mitad de una ejecucion.
 */
@Service
public class RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<EstadoCita> ESTADOS_A_NOTIFICAR = List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);

    private final CitaRepository citaRepository;
    private final NotificacionService notificacionService;

    public RecordatorioService(CitaRepository citaRepository, NotificacionService notificacionService) {
        this.citaRepository = citaRepository;
        this.notificacionService = notificacionService;
    }

    // zone = "America/Lima" se mantiene explicito aqui como defensa adicional,
    // ademas del ajuste global de zona horaria de la JVM en
    // LuasPetsApplication (ver el comentario alli para la justificacion
    // completa de por que se eligio el ajuste global y no solo este atributo).
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Lima")
    public void enviarRecordatoriosDelDiaSiguiente() {
        procesarRecordatorios(LocalDate.now().plusDays(1));
    }

    // Separado del metodo anotado con @Scheduled para poder probarlo con una
    // fecha controlada, sin depender del reloj ni esperar a las 8 de la
    // manana.
    @Transactional
    public int procesarRecordatorios(LocalDate fechaObjetivo) {
        LocalDateTime desde = LocalDateTime.of(fechaObjetivo, LocalTime.MIN);
        LocalDateTime hasta = LocalDateTime.of(fechaObjetivo, LocalTime.of(23, 59, 59));

        List<Cita> citas = citaRepository.findByFechaHoraBetweenAndEstadoIn(desde, hasta, ESTADOS_A_NOTIFICAR);

        int notificacionesCreadas = 0;
        for (Cita cita : citas) {
            try {
                notificacionesCreadas += procesarCita(cita);
            } catch (Exception e) {
                log.error("No se pudo generar el recordatorio de la cita {}", cita.getId(), e);
            }
        }

        log.info("Recordatorios del {}: {} citas procesadas, {} notificaciones creadas",
                fechaObjetivo, citas.size(), notificacionesCreadas);
        return notificacionesCreadas;
    }

    private int procesarCita(Cita cita) {
        int creadas = 0;
        Mascota mascota = cita.getMascota();
        Usuario cliente = mascota.getCliente();
        Usuario doctor = cita.getDoctor();
        String hora = cita.getFechaHora().format(FORMATO_HORA);

        String urlCliente = "/cliente/citas?cita=" + cita.getId();
        if (!yaExisteRecordatorio(cliente, urlCliente)) {
            String mensajeCliente = mascota.getNombre() + " tiene cita mañana a las " + hora + " con Dr. "
                    + doctor.getNombre() + " " + doctor.getApellido() + textoMotivo(cita.getMotivo());
            notificacionService.crear(cliente, TipoNotificacion.CITA_RECORDATORIO, "Recordatorio: cita mañana",
                    mensajeCliente, urlCliente);
            creadas++;
        }

        String urlDoctor = "/doctor/citas?cita=" + cita.getId();
        if (!yaExisteRecordatorio(doctor, urlDoctor)) {
            String mensajeDoctor = "Mañana a las " + hora + " atiendes a " + mascota.getNombre() + " ("
                    + mascota.getEspecie() + "). Propietario: " + cliente.getNombre() + " " + cliente.getApellido()
                    + ".";
            notificacionService.crear(doctor, TipoNotificacion.CITA_RECORDATORIO, "Recordatorio: cita mañana",
                    mensajeDoctor, urlDoctor);
            creadas++;
        }

        return creadas;
    }

    private boolean yaExisteRecordatorio(Usuario destinatario, String url) {
        return notificacionService.existeNotificacion(destinatario.getId(), TipoNotificacion.CITA_RECORDATORIO, url);
    }

    private String textoMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return ".";
        }
        return ". Motivo: " + motivo + ".";
    }
}
