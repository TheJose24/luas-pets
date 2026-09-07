package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Notificacion;
import com.luaspets.model.Rol;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.NotificacionRepository;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.service.NotificacionService;
import com.luaspets.service.RecordatorioService;

/**
 * Cubre RecordatorioService.procesarRecordatorios: que notifique al cliente y
 * al doctor de las citas PENDIENTE/CONFIRMADA del dia objetivo, que ignore
 * otros estados y otros dias, que el control de duplicados por
 * destinatario+tipo+url evite duplicar al ejecutarse dos veces, y que un
 * fallo en una cita no aborte el procesamiento de las demas.
 *
 * No usa MockMvc: procesarRecordatorios se prueba invocandolo directamente
 * con una fecha controlada, tal como pide la tarea (sin depender del reloj ni
 * de la hora a la que corra el @Scheduled).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecordatorioServiceTest {

    @Autowired
    private RecordatorioService recordatorioService;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @MockitoSpyBean
    private NotificacionService notificacionService;

    private long contadorEmail = 0;

    private Usuario crearUsuario(Rol rol) {
        contadorEmail++;
        Usuario usuario = new Usuario();
        usuario.setNombre("Nombre" + contadorEmail);
        usuario.setApellido("Apellido" + contadorEmail);
        usuario.setEmail("recordatorio" + contadorEmail + "@test.com");
        usuario.setPassword("hash-no-relevante");
        usuario.setTelefono("987654321");
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setFechaRegistro(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    private Mascota crearMascotaPara(Usuario cliente) {
        Mascota mascota = new Mascota();
        mascota.setNombre("Rocky");
        mascota.setEspecie("Perro");
        mascota.setCliente(cliente);
        return mascotaRepository.save(mascota);
    }

    // Construida directamente por repositorio (no via CitaService.agendarCita):
    // aqui no interesa la validacion de horario/anticipacion de citas, solo
    // tener una Cita con la fechaHora y el estado exactos que cada escenario
    // necesita.
    private Cita crearCitaDirecta(Mascota mascota, Usuario doctor, LocalDateTime fechaHora, EstadoCita estado,
            String motivo) {
        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaHora);
        cita.setMotivo(motivo);
        cita.setEstado(estado);
        cita.setFechaCreacion(LocalDateTime.now());
        return citaRepository.save(cita);
    }

    @Test
    void procesarRecordatoriosCreaDosNotificacionesParaUnaCitaPendienteDelDiaObjetivo() {
        Usuario cliente = crearUsuario(Rol.CLIENTE);
        Usuario doctor = crearUsuario(Rol.DOCTOR);
        Mascota mascota = crearMascotaPara(cliente);
        LocalDate manana = LocalDate.now().plusDays(1);
        Cita cita = crearCitaDirecta(mascota, doctor, LocalDateTime.of(manana, LocalTime.of(10, 30)),
                EstadoCita.PENDIENTE, "Vacunación anual");

        int creadas = recordatorioService.procesarRecordatorios(manana);

        assertThat(creadas).isEqualTo(2);

        var notifsCliente = notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(cliente.getId());
        assertThat(notifsCliente).hasSize(1);
        Notificacion notifCliente = notifsCliente.get(0);
        assertThat(notifCliente.getTipo()).isEqualTo(TipoNotificacion.CITA_RECORDATORIO);
        assertThat(notifCliente.getUrl()).isEqualTo("/cliente/citas?cita=" + cita.getId());
        assertThat(notifCliente.getTitulo()).isEqualTo("Recordatorio: cita mañana");
        assertThat(notifCliente.getMensaje()).contains("Rocky").contains("10:30").contains("Vacunación anual");

        var notifsDoctor = notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(doctor.getId());
        assertThat(notifsDoctor).hasSize(1);
        Notificacion notifDoctor = notifsDoctor.get(0);
        assertThat(notifDoctor.getTipo()).isEqualTo(TipoNotificacion.CITA_RECORDATORIO);
        assertThat(notifDoctor.getUrl()).isEqualTo("/doctor/citas?cita=" + cita.getId());
        assertThat(notifDoctor.getMensaje()).contains("Rocky").contains("Perro").contains("10:30");
    }

    @Test
    void procesarRecordatoriosCreaDosNotificacionesParaUnaCitaConfirmadaDelDiaObjetivo() {
        Usuario cliente = crearUsuario(Rol.CLIENTE);
        Usuario doctor = crearUsuario(Rol.DOCTOR);
        Mascota mascota = crearMascotaPara(cliente);
        LocalDate manana = LocalDate.now().plusDays(1);
        crearCitaDirecta(mascota, doctor, LocalDateTime.of(manana, LocalTime.of(15, 0)), EstadoCita.CONFIRMADA,
                "Control de peso");

        int creadas = recordatorioService.procesarRecordatorios(manana);

        assertThat(creadas).isEqualTo(2);
    }

    @Test
    void procesarRecordatoriosNoCreaNotificacionesParaCitasAtendidasNiCanceladas() {
        Usuario cliente = crearUsuario(Rol.CLIENTE);
        Usuario doctor = crearUsuario(Rol.DOCTOR);
        Mascota mascota = crearMascotaPara(cliente);
        LocalDate manana = LocalDate.now().plusDays(1);
        crearCitaDirecta(mascota, doctor, LocalDateTime.of(manana, LocalTime.of(9, 0)), EstadoCita.ATENDIDA, "x");
        crearCitaDirecta(mascota, doctor, LocalDateTime.of(manana, LocalTime.of(11, 0)), EstadoCita.CANCELADA, "x");

        int creadas = recordatorioService.procesarRecordatorios(manana);

        assertThat(creadas).isEqualTo(0);
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(cliente.getId())).isEmpty();
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(doctor.getId())).isEmpty();
    }

    @Test
    void procesarRecordatoriosNoCreaNotificacionesParaCitasDeOtrosDias() {
        Usuario cliente = crearUsuario(Rol.CLIENTE);
        Usuario doctor = crearUsuario(Rol.DOCTOR);
        Mascota mascota = crearMascotaPara(cliente);
        LocalDate hoy = LocalDate.now();
        LocalDate manana = hoy.plusDays(1);
        LocalDate pasadoManana = hoy.plusDays(2);

        crearCitaDirecta(mascota, doctor, LocalDateTime.of(hoy, LocalTime.of(10, 0)), EstadoCita.PENDIENTE, "x");
        crearCitaDirecta(mascota, doctor, LocalDateTime.of(pasadoManana, LocalTime.of(10, 0)), EstadoCita.PENDIENTE,
                "x");

        int creadas = recordatorioService.procesarRecordatorios(manana);

        assertThat(creadas).isEqualTo(0);
    }

    @Test
    void ejecutarProcesarRecordatoriosDosVecesConLaMismaFechaNoDuplicaNotificaciones() {
        Usuario cliente = crearUsuario(Rol.CLIENTE);
        Usuario doctor = crearUsuario(Rol.DOCTOR);
        Mascota mascota = crearMascotaPara(cliente);
        LocalDate manana = LocalDate.now().plusDays(1);
        crearCitaDirecta(mascota, doctor, LocalDateTime.of(manana, LocalTime.of(10, 0)), EstadoCita.PENDIENTE, "x");

        int primeraEjecucion = recordatorioService.procesarRecordatorios(manana);
        int segundaEjecucion = recordatorioService.procesarRecordatorios(manana);

        assertThat(primeraEjecucion).isEqualTo(2);
        assertThat(segundaEjecucion).isEqualTo(0);
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(cliente.getId())).hasSize(1);
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(doctor.getId())).hasSize(1);
    }

    @Test
    void siUnaCitaFallaLasDemasSeProcesanIgualmente() {
        Usuario clienteProblematico = crearUsuario(Rol.CLIENTE);
        Usuario doctorComun = crearUsuario(Rol.DOCTOR);
        Mascota mascotaProblematica = crearMascotaPara(clienteProblematico);

        Usuario clienteNormal = crearUsuario(Rol.CLIENTE);
        Mascota mascotaNormal = crearMascotaPara(clienteNormal);

        LocalDate manana = LocalDate.now().plusDays(1);
        crearCitaDirecta(mascotaProblematica, doctorComun, LocalDateTime.of(manana, LocalTime.of(9, 0)),
                EstadoCita.PENDIENTE, "Cita con datos inconsistentes");
        crearCitaDirecta(mascotaNormal, doctorComun, LocalDateTime.of(manana, LocalTime.of(10, 0)),
                EstadoCita.PENDIENTE, "Cita normal");

        // Simula datos inconsistentes (por ejemplo, una mascota sin cliente
        // valido) forzando que la notificacion al cliente de la PRIMERA cita
        // lance una excepcion, sin tocar la base de datos ni el esquema (la
        // columna cliente_id es NOT NULL, asi que una mascota "sin cliente"
        // real no se puede persistir; esto reproduce el mismo efecto donde
        // realmente importa: que procesarCita() reciba una excepcion a mitad
        // de camino).
        doThrow(new RuntimeException("Datos inconsistentes simulados"))
                .when(notificacionService)
                .crear(eq(clienteProblematico), any(), anyString(), anyString(), anyString());

        int creadas = recordatorioService.procesarRecordatorios(manana);

        // La cita problematica no genera ninguna notificacion (fallo antes de
        // llegar a la del doctor), pero la cita normal si genera sus dos.
        assertThat(creadas).isEqualTo(2);
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(clienteProblematico.getId()))
                .isEmpty();
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(clienteNormal.getId()))
                .hasSize(1);
        assertThat(notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(doctorComun.getId()))
                .hasSize(1);
    }

    @Test
    void lasNotificacionesCreadasTienenTipoRecordatorioYUrlConElIdDeLaCita() {
        Usuario cliente = crearUsuario(Rol.CLIENTE);
        Usuario doctor = crearUsuario(Rol.DOCTOR);
        Mascota mascota = crearMascotaPara(cliente);
        LocalDate manana = LocalDate.now().plusDays(1);
        Cita cita = crearCitaDirecta(mascota, doctor, LocalDateTime.of(manana, LocalTime.of(10, 0)),
                EstadoCita.PENDIENTE, "x");

        recordatorioService.procesarRecordatorios(manana);

        Notificacion notifCliente = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(cliente.getId()).get(0);
        Notificacion notifDoctor = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(doctor.getId()).get(0);

        assertThat(notifCliente.getTipo()).isEqualTo(TipoNotificacion.CITA_RECORDATORIO);
        assertThat(notifCliente.getUrl()).contains(String.valueOf(cita.getId()));
        assertThat(notifDoctor.getTipo()).isEqualTo(TipoNotificacion.CITA_RECORDATORIO);
        assertThat(notifDoctor.getUrl()).contains(String.valueOf(cita.getId()));
    }
}
