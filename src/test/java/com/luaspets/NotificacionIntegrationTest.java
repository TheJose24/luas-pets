package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.model.Cita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Notificacion;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.NotificacionRepository;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.service.CitaService;
import com.luaspets.service.NotificacionService;

/**
 * Cubre la generacion de notificaciones por eventos de negocio y las reglas
 * de acceso del servicio de notificaciones (propiedad, marcar todas como
 * leidas, autenticacion requerida en la API).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificacionIntegrationTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private CitaService citaService;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    private MockHttpSession registrarYLoguearCliente(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc().perform(post("/registro").with(csrf()).session(session)
                .param("nombre", "Cliente")
                .param("apellido", "DePrueba")
                .param("email", email)
                .param("password", "clave123")
                .param("confirmPassword", "clave123")
                .param("telefono", "987654321"));
        mvc().perform(post("/login").with(csrf()).session(session)
                .param("username", email)
                .param("password", "clave123"));
        return session;
    }

    private MockHttpSession loguearComo(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc().perform(post("/login").with(csrf()).session(session)
                .param("username", email)
                .param("password", password));
        return session;
    }

    private Mascota crearMascotaPara(Usuario cliente) {
        Mascota mascota = new Mascota();
        mascota.setNombre("Luna");
        mascota.setEspecie("Gato");
        mascota.setCliente(cliente);
        return mascotaRepository.save(mascota);
    }

    private Usuario doctorSeed() {
        return usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();
    }

    // Hora fija (14:00) y 2 dias de adelanto para quedar siempre dentro del
    // horario de atencion (08:00-20:00) y muy por encima del minimo de 24
    // horas de anticipacion, sin importar a que hora corra la suite. Se usa
    // 14:00 y no 10:00 para no chocar con la cita que DataSeeder siembra para
    // doctor@luaspets.com en dia+3 a las 10:00.
    private String fechaFuturaTexto() {
        return LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0).format(FORMAT);
    }

    @Test
    void agendarCitaGeneraNotificacionParaElDoctor() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("notif.agendar@test.com");
        Usuario cliente = usuarioRepository.findByEmail("notif.agendar@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto();

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Vacuna"));

        List<Notificacion> notificacionesDoctor = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(doctor.getId());
        assertThat(notificacionesDoctor).anySatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo(TipoNotificacion.CITA_AGENDADA);
            assertThat(n.getTitulo()).isEqualTo("Nueva cita agendada");
            assertThat(n.getMensaje()).contains("Luna").contains("Vacuna");
            assertThat(n.getUrl()).isEqualTo("/doctor/citas");
            assertThat(n.getLeida()).isFalse();
        });
    }

    @Test
    void confirmarCitaGeneraNotificacionParaElClienteDuenoDeLaMascota() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("notif.confirmar@test.com");
        Usuario cliente = usuarioRepository.findByEmail("notif.confirmar@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto();

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Control"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/confirmar", cita.getId()).with(csrf()).session(sesionDoctor))
                .andExpect(status().is3xxRedirection());

        List<Notificacion> notificacionesCliente = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(cliente.getId());
        assertThat(notificacionesCliente).anySatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo(TipoNotificacion.CITA_CONFIRMADA);
            assertThat(n.getTitulo()).isEqualTo("Tu cita fue confirmada");
            assertThat(n.getUrl()).isEqualTo("/cliente/citas");
            assertThat(n.getLeida()).isFalse();
        });
    }

    @Test
    void usuarioRecibe403AlMarcarComoLeidaUnaNotificacionDeOtroUsuario() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("notif.propietarioA@test.com");
        Usuario clienteA = usuarioRepository.findByEmail("notif.propietarioA@test.com").orElseThrow();
        notificacionService.crear(clienteA, TipoNotificacion.CITA_ATENDIDA, "Titulo", "Mensaje", "/cliente/mascotas");
        Notificacion notificacionDeA = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(clienteA.getId()).get(0);

        registrarYLoguearCliente("notif.intrusoB@test.com");
        Usuario clienteB = usuarioRepository.findByEmail("notif.intrusoB@test.com").orElseThrow();

        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class,
                () -> notificacionService.marcarComoLeida(notificacionDeA.getId(), clienteB.getId()));

        assertThat(notificacionRepository.findById(notificacionDeA.getId()).orElseThrow().getLeida()).isFalse();
    }

    @Test
    void marcarTodasComoLeidasDejaElConteoDeNoLeidasEnCero() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("notif.marcartodas@test.com");
        Usuario cliente = usuarioRepository.findByEmail("notif.marcartodas@test.com").orElseThrow();
        notificacionService.crear(cliente, TipoNotificacion.CITA_ATENDIDA, "T1", "M1", null);
        notificacionService.crear(cliente, TipoNotificacion.CITA_CONFIRMADA, "T2", "M2", null);
        notificacionService.crear(cliente, TipoNotificacion.PEDIDO_ESTADO, "T3", "M3", null);

        assertThat(notificacionService.contarNoLeidas(cliente.getId())).isEqualTo(3);

        mvc().perform(post("/api/notificaciones/leer-todas").with(csrf()).session(sesion))
                .andExpect(status().isOk());

        assertThat(notificacionService.contarNoLeidas(cliente.getId())).isEqualTo(0);
    }

    @Test
    void getApiNotificacionesSinAutenticarNoDevuelveContenido() throws Exception {
        int status = mvc().perform(get("/api/notificaciones"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(302, 401, 403);
    }

    @Test
    void paginaDeNotificacionesRenderizaSinErrorParaLosTresRoles() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("notif.pagina.cliente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("notif.pagina.cliente@test.com").orElseThrow();
        notificacionService.crear(cliente, TipoNotificacion.CITA_ATENDIDA, "Título leída", "Mensaje leído",
                "/cliente/mascotas");
        notificacionService.crear(cliente, TipoNotificacion.PEDIDO_ESTADO, "Título no leída", "Mensaje no leído",
                null);
        Notificacion leida = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(cliente.getId()).stream()
                .filter(n -> n.getTipo() == TipoNotificacion.CITA_ATENDIDA)
                .findFirst().orElseThrow();
        notificacionService.marcarComoLeida(leida.getId(), cliente.getId());

        mvc().perform(get("/notificaciones").session(sesionCliente))
                .andExpect(status().isOk());

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(get("/notificaciones").session(sesionDoctor))
                .andExpect(status().isOk());

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        mvc().perform(get("/notificaciones").session(sesionAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void paginaDeNotificacionesMuestraEstadoVacioSinNinguna() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("notif.pagina.vacia@test.com");
        mvc().perform(get("/notificaciones").session(sesion))
                .andExpect(status().isOk());
    }
}
