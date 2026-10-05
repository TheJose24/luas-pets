package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.exception.BusinessException;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.EstadoMascota;
import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.service.CitaService;

/**
 * Cubre las reglas de negocio de citas: agendar, doble reserva del mismo doctor,
 * reprogramar, rechazo de cancelacion sobre una cita atendida, transicion a ATENDIDA
 * al registrar historial medico, y aislamiento entre clientes (403).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CitaIntegrationTest {

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
        if (email.equals("admin@luaspets.com")) {
            return MfaTestSupport.adminLogin(mvc(), webApplicationContext, email, password);
        }
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

    private Usuario doctorSecundarioSeed() {
        return usuarioRepository.findByEmail("lucio@gmail.com").orElseThrow();
    }

    // Helper para construir fechas de cita validas independientemente de la
    // hora a la que corra la suite: fija la hora a las 14:00 (dentro de
    // 08:00-20:00) y usa un minimo de 2 dias de adelanto, que siempre deja un
    // margen de al menos ~34 horas respecto a "ahora" (mas que las 24 horas
    // minimas exigidas por CitaService.validarHorario), evitando fallos
    // intermitentes por estar justo en el limite. Se eligio 14:00 (y no 10:00)
    // a proposito: DataSeeder siembra una cita PENDIENTE para doctor@luaspets.com
    // en "ahora + 3 dias a las 10:00", y usar la misma hora aqui chocaria con
    // la validacion de "el doctor ya tiene una cita en ese horario" cuando el
    // dia calculado coincidiera con el del seeder.
    private LocalDateTime fechaFutura(long diasAdelante) {
        return LocalDateTime.now().plusDays(diasAdelante).withHour(14).withMinute(0).withSecond(0).withNano(0);
    }

    private String fechaFuturaTexto(long diasAdelante) {
        return fechaFutura(diasAdelante).format(FORMAT);
    }

    @Test
    void agendarCitaCasoFeliz() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("agenda.feliz@test.com");
        Usuario cliente = usuarioRepository.findByEmail("agenda.feliz@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(session)
                        .param("mascotaId", mascota.getId().toString())
                        .param("doctorId", doctor.getId().toString())
                        .param("fechaHora", fecha)
                        .param("motivo", "Chequeo general"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas?exito"));

        var citas = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId());
        assertThat(citas).hasSize(1);
        assertThat(citas.get(0).getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(citas.get(0).getMotivo()).isEqualTo("Chequeo general");
    }

    @Test
    void dosCitasMismoDoctorMismaHoraLaSegundaFalla() throws Exception {
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        MockHttpSession sesionA = registrarYLoguearCliente("clienteA.horario@test.com");
        Usuario clienteA = usuarioRepository.findByEmail("clienteA.horario@test.com").orElseThrow();
        Mascota mascotaA = crearMascotaPara(clienteA);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionA)
                        .param("mascotaId", mascotaA.getId().toString())
                        .param("doctorId", doctor.getId().toString())
                        .param("fechaHora", fecha)
                        .param("motivo", "Primera cita"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas?exito"));

        MockHttpSession sesionB = registrarYLoguearCliente("clienteB.horario@test.com");
        Usuario clienteB = usuarioRepository.findByEmail("clienteB.horario@test.com").orElseThrow();
        Mascota mascotaB = crearMascotaPara(clienteB);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionB)
                        .param("mascotaId", mascotaB.getId().toString())
                        .param("doctorId", doctor.getId().toString())
                        .param("fechaHora", fecha)
                        .param("motivo", "Segunda cita, mismo horario"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas/nueva"))
                .andExpect(flash().attribute("error", "El doctor ya tiene una cita agendada en ese horario"));

        assertThat(citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(clienteB.getId())).isEmpty();
    }

    @Test
    void reprogramarCitaCambiaLaFecha() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("reprograma@test.com");
        Usuario cliente = usuarioRepository.findByEmail("reprograma@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fechaOriginal = fechaFuturaTexto(3);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(session)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fechaOriginal)
                .param("motivo", "Cita a reprogramar"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);
        LocalDateTime nuevaFecha = fechaFutura(5);

        mvc().perform(post("/cliente/citas/{id}/reprogramar", cita.getId()).with(csrf()).session(session)
                        .param("nuevaFechaHora", nuevaFecha.format(FORMAT)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas?reprogramada"));

        Cita citaActualizada = citaRepository.findById(cita.getId()).orElseThrow();
        assertThat(citaActualizada.getFechaHora()).isEqualTo(nuevaFecha);
    }

    @Test
    void cancelarCitaAtendidaEsRechazada() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("cancela.atendida@test.com");
        Usuario cliente = usuarioRepository.findByEmail("cancela.atendida@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita que sera atendida"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                        .param("diagnostico", "Todo en orden")
                        .param("tratamiento", "Ninguno")
                        .param("observaciones", "Sin novedad"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctor/citas?atendida"));

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.ATENDIDA);

        mvc().perform(post("/cliente/citas/{id}/cancelar", cita.getId()).with(csrf()).session(sesionCliente))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas"))
                .andExpect(flash().attribute("error", "No se puede cancelar una cita ya atendida"));

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.ATENDIDA);
    }

    @Test
    void registrarHistorialMedicoMarcaLaCitaComoAtendida() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("historial@test.com");
        Usuario cliente = usuarioRepository.findByEmail("historial@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Consulta con historial"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                        .param("diagnostico", "Otitis leve")
                        .param("tratamiento", "Gotas oticas por 7 dias")
                        .param("observaciones", "Control en una semana"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctor/citas?atendida"));

        Cita citaActualizada = citaRepository.findById(cita.getId()).orElseThrow();
        assertThat(citaActualizada.getEstado()).isEqualTo(EstadoCita.ATENDIDA);
    }

    @Test
    void registrarHistorialConPesoRegistradoActualizaElPesoDeLaMascota() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("peso.mascota@test.com");
        Usuario cliente = usuarioRepository.findByEmail("peso.mascota@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        mascota.setPeso(new BigDecimal("10.00"));
        mascota = mascotaRepository.save(mascota);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Control de peso"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                        .param("diagnostico", "Buen estado general")
                        .param("tratamiento", "Ninguno")
                        .param("pesoRegistrado", "11.50")
                        .param("medicamentos", "Vitamina C — 1 tableta al día"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctor/citas?atendida"));

        Mascota mascotaActualizada = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertThat(mascotaActualizada.getPeso()).isEqualByComparingTo(new BigDecimal("11.50"));
    }

    @Test
    void registrarHistorialSinPesoRegistradoNoModificaElPesoDeLaMascota() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("sin.peso.mascota@test.com");
        Usuario cliente = usuarioRepository.findByEmail("sin.peso.mascota@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        mascota.setPeso(new BigDecimal("10.00"));
        mascota = mascotaRepository.save(mascota);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Consulta sin pesaje"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                        .param("diagnostico", "Solo revision visual, sin pesaje")
                        .param("tratamiento", "Ninguno"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctor/citas?atendida"));

        Mascota mascotaSinCambios = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertThat(mascotaSinCambios.getPeso()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void mascotaConCamposOpcionalesNulosSeRenderizaSinErrorEnLasTresVistas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("campos.nulos@test.com");
        Usuario cliente = usuarioRepository.findByEmail("campos.nulos@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        mascota.setEstado(null);
        mascota.setSexo(null);
        mascota.setAlergias(null);
        mascota = mascotaRepository.save(mascota);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Chequeo con campos nulos"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                .param("diagnostico", "Sin novedad")
                .param("tratamiento", "Ninguno"));

        mvc().perform(get("/cliente/mascotas/{id}", mascota.getId()).session(sesionCliente))
                .andExpect(status().isOk());

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        mvc().perform(get("/admin/mascotas").session(sesionAdmin))
                .andExpect(status().isOk());

        mvc().perform(get("/doctor/pacientes/{id}", mascota.getId()).session(sesionDoctor))
                .andExpect(status().isOk());
    }

    @Test
    void adminMascotasPaginaCorrectamenteConMasDeUnaPaginaDeResultados() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.paginacion@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.paginacion@test.com").orElseThrow();
        for (int i = 0; i < 12; i++) {
            Mascota mascota = new Mascota();
            mascota.setNombre("Paginada" + i);
            mascota.setEspecie("Gato");
            mascota.setCliente(cliente);
            mascotaRepository.save(mascota);
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/mascotas").session(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                        .attribute("page", org.hamcrest.Matchers.hasProperty("totalPages",
                                org.hamcrest.Matchers.greaterThanOrEqualTo(2))));

        mvc().perform(get("/admin/mascotas").session(sesionAdmin).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                        .attribute("page", org.hamcrest.Matchers.hasProperty("number",
                                org.hamcrest.Matchers.equalTo(1))));
    }

    @Test
    void clienteNoPuedeAccederALaCitaDeOtroCliente() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("propietarioA@test.com");
        Usuario clienteA = usuarioRepository.findByEmail("propietarioA@test.com").orElseThrow();
        Mascota mascotaA = crearMascotaPara(clienteA);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionA)
                .param("mascotaId", mascotaA.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita de A"));

        Cita citaDeA = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(clienteA.getId()).get(0);

        MockHttpSession sesionB = registrarYLoguearCliente("intrusoB@test.com");

        mvc().perform(get("/cliente/citas/{id}/reprogramar", citaDeA.getId()).session(sesionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorAccedeALaFichaDeUnPacienteQueHaAtendido() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("paciente.ficha@test.com");
        Usuario cliente = usuarioRepository.findByEmail("paciente.ficha@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Chequeo general"));

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(get("/doctor/pacientes/{id}", mascota.getId()).session(sesionDoctor))
                .andExpect(status().isOk());
    }

    @Test
    void doctorRecibe403EnFichaDePacienteQueNuncaHaAtendido() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("paciente.ajeno@test.com");
        Usuario cliente = usuarioRepository.findByEmail("paciente.ajeno@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(get("/doctor/pacientes/{id}", mascota.getId()).session(sesionDoctor))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardYAgendaDelDoctorSeRenderizanCorrectamente() throws Exception {
        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");

        mvc().perform(get("/doctor/dashboard").session(sesionDoctor))
                .andExpect(status().isOk());

        mvc().perform(get("/doctor/citas").session(sesionDoctor))
                .andExpect(status().isOk());
    }

    @Test
    void dashboardDelAdminSeRenderizaCorrectamenteConDatosReales() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("admin.dashboard@test.com");
        Usuario cliente = usuarioRepository.findByEmail("admin.dashboard@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita para el dashboard admin"));

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        mvc().perform(get("/admin/dashboard").session(sesionAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void clienteRecibe403AlAccederAAdminMascotas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("intruso.mascotas@test.com");

        mvc().perform(get("/admin/mascotas").session(sesionCliente))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListaFiltraYCambiaElEstadoDeUnaMascota() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.admin.mascotas@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.admin.mascotas@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        assertThat(mascota.getEstado()).isEqualTo(EstadoMascota.ACTIVO);

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/mascotas").session(sesionAdmin))
                .andExpect(status().isOk());

        mvc().perform(get("/admin/mascotas")
                        .session(sesionAdmin)
                        .param("buscar", "Luna")
                        .param("especie", "Gato")
                        .param("estado", "ACTIVO"))
                .andExpect(status().isOk());

        mvc().perform(post("/admin/mascotas/{id}/estado", mascota.getId()).with(csrf()).session(sesionAdmin)
                        .param("nuevoEstado", "EN_TRATAMIENTO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mascotas"))
                .andExpect(flash().attribute("exito", "Estado actualizado correctamente."));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMascota.EN_TRATAMIENTO);
    }

    @Test
    void doctorAgendaEnVistaCalendarioResponde200() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("calendario.doctor@test.com");
        Usuario cliente = usuarioRepository.findByEmail("calendario.doctor@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita para el calendario del doctor"));

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");

        mvc().perform(get("/doctor/citas").session(sesionDoctor).param("vista", "calendario"))
                .andExpect(status().isOk());

        mvc().perform(get("/doctor/citas").session(sesionDoctor)
                        .param("vista", "calendario")
                        .param("estado", "PENDIENTE")
                        .param("buscar", "Luna"))
                .andExpect(status().isOk());

        mvc().perform(get("/doctor/citas").session(sesionDoctor).param("vista", "lista"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCitasEnVistaCalendarioYListaResponden200() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("calendario.admin@test.com");
        Usuario cliente = usuarioRepository.findByEmail("calendario.admin@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita para el calendario admin"));

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/citas").session(sesionAdmin).param("vista", "calendario"))
                .andExpect(status().isOk());

        mvc().perform(get("/admin/citas").session(sesionAdmin)
                        .param("vista", "calendario")
                        .param("estado", "PENDIENTE")
                        .param("doctorId", doctor.getId().toString())
                        .param("especie", "Gato")
                        .param("buscar", "Luna"))
                .andExpect(status().isOk());

        mvc().perform(get("/admin/citas").session(sesionAdmin).param("vista", "lista"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmarCitaPendienteLaDejaEnConfirmada() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("confirmar.pendiente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("confirmar.pendiente@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita a confirmar"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/confirmar", cita.getId()).with(csrf()).session(sesionDoctor))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctor/citas"))
                .andExpect(flash().attribute("exito", "Cita confirmada correctamente."));

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.CONFIRMADA);
    }

    @Test
    void confirmarCitaYaConfirmadaFallaConBusinessExceptionYNoCambiaElEstado() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("confirmar.doble@test.com");
        Usuario cliente = usuarioRepository.findByEmail("confirmar.doble@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita ya confirmada"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);
        citaService.confirmarCita(cita.getId());
        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.CONFIRMADA);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> citaService.confirmarCita(cita.getId()));
        assertThat(ex.getMessage()).isEqualTo("Solo se pueden confirmar citas pendientes");

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.CONFIRMADA);
    }

    @Test
    void confirmarCitaAtendidaFallaConBusinessException() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("confirmar.atendida@test.com");
        Usuario cliente = usuarioRepository.findByEmail("confirmar.atendida@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita que sera atendida antes de confirmar"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                .param("diagnostico", "Todo en orden")
                .param("tratamiento", "Ninguno"));

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.ATENDIDA);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> citaService.confirmarCita(cita.getId()));
        assertThat(ex.getMessage()).isEqualTo("Solo se pueden confirmar citas pendientes");

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.ATENDIDA);
    }

    @Test
    void doctorRecibe403AlConfirmarCitaQueNoEsSuya() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("confirmar.ajena@test.com");
        Usuario cliente = usuarioRepository.findByEmail("confirmar.ajena@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita de otro doctor"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);

        MockHttpSession sesionOtroDoctor = loguearComo("lucio@gmail.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/confirmar", cita.getId()).with(csrf()).session(sesionOtroDoctor))
                .andExpect(status().isForbidden());

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void citaConfirmadaSiguePudiendoReprogramarseYCancelarse() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("confirmar.luego.reprograma@test.com");
        Usuario cliente = usuarioRepository.findByEmail("confirmar.luego.reprograma@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();
        String fecha = fechaFuturaTexto(2);

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fecha)
                .param("motivo", "Cita a confirmar, reprogramar y cancelar"));

        Cita cita = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId()).get(0);
        citaService.confirmarCita(cita.getId());
        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.CONFIRMADA);

        LocalDateTime nuevaFecha = fechaFutura(6);
        mvc().perform(post("/cliente/citas/{id}/reprogramar", cita.getId()).with(csrf()).session(sesionCliente)
                        .param("nuevaFechaHora", nuevaFecha.format(FORMAT)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas?reprogramada"));

        Cita citaReprogramada = citaRepository.findById(cita.getId()).orElseThrow();
        assertThat(citaReprogramada.getFechaHora()).isEqualTo(nuevaFecha);
        assertThat(citaReprogramada.getEstado()).isEqualTo(EstadoCita.CONFIRMADA);

        mvc().perform(post("/cliente/citas/{id}/cancelar", cita.getId()).with(csrf()).session(sesionCliente))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas"));

        assertThat(citaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }

    @Test
    void agendarCitaA0759FallaConMensajeDeHorario() throws Exception {
        registrarYLoguearCliente("horario.0759@test.com");
        Usuario cliente = usuarioRepository.findByEmail("horario.0759@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaFutura(2).withHour(7).withMinute(59));
        cita.setMotivo("Muy temprano");

        BusinessException ex = assertThrows(BusinessException.class, () -> citaService.agendarCita(cita));
        assertThat(ex.getMessage()).isEqualTo("El horario de atención es de 08:00 a 20:00");
    }

    @Test
    void agendarCitaALas2000ExactasTieneExito() throws Exception {
        registrarYLoguearCliente("horario.2000@test.com");
        Usuario cliente = usuarioRepository.findByEmail("horario.2000@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaFutura(2).withHour(20).withMinute(0));
        cita.setMotivo("Ultimo turno del dia");

        Cita guardada = citaService.agendarCita(cita);
        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void agendarCitaALas2001FallaConMensajeDeHorario() throws Exception {
        registrarYLoguearCliente("horario.2001@test.com");
        Usuario cliente = usuarioRepository.findByEmail("horario.2001@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaFutura(2).withHour(20).withMinute(1));
        cita.setMotivo("Un minuto tarde");

        BusinessException ex = assertThrows(BusinessException.class, () -> citaService.agendarCita(cita));
        assertThat(ex.getMessage()).isEqualTo("El horario de atención es de 08:00 a 20:00");
    }

    @Test
    void agendarCitaDentroDeLasProximas24HorasFallaConMensajeDeAnticipacion() throws Exception {
        registrarYLoguearCliente("anticipacion.insuficiente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("anticipacion.insuficiente@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        // A solo 5 horas vista: sin importar la hora del dia en que caiga, la
        // validacion de anticipacion se evalua antes que la de horario (segun
        // el orden documentado en CitaService.validarHorario), asi que este
        // mensaje es siempre el que se lanza.
        cita.setFechaHora(LocalDateTime.now().plusHours(5));
        cita.setMotivo("Muy pronto");

        BusinessException ex = assertThrows(BusinessException.class, () -> citaService.agendarCita(cita));
        assertThat(ex.getMessage()).isEqualTo("Las citas deben agendarse con al menos 24 horas de anticipación");
    }

    @Test
    void agendarCitaConMasDe24HorasDeAnticipacionALas1000TieneExito() throws Exception {
        registrarYLoguearCliente("anticipacion.suficiente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("anticipacion.suficiente@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        // fechaFutura(2) deja siempre un margen minimo de ~34 horas (ver el
        // comentario del helper), comodamente por encima de las 24 horas
        // minimas exigidas.
        LocalDateTime fecha = fechaFutura(2);
        assertThat(java.time.Duration.between(LocalDateTime.now(), fecha).toHours()).isGreaterThanOrEqualTo(25);

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fecha);
        cita.setMotivo("Con anticipacion suficiente");

        Cita guardada = citaService.agendarCita(cita);
        assertThat(guardada.getId()).isNotNull();
    }

    @Test
    void agendarCitaUnDomingoDentroDelHorarioTieneExito() throws Exception {
        registrarYLoguearCliente("domingo.atencion@test.com");
        Usuario cliente = usuarioRepository.findByEmail("domingo.atencion@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        LocalDateTime candidato = fechaFutura(2);
        while (candidato.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            candidato = candidato.plusDays(1);
        }

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(candidato);
        cita.setMotivo("Cita de domingo");

        Cita guardada = citaService.agendarCita(cita);
        assertThat(guardada.getFechaHora().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
        assertThat(guardada.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void reprogramarAUnaHoraFueraDelHorarioFalla() throws Exception {
        registrarYLoguearCliente("reprograma.horario.invalido@test.com");
        Usuario cliente = usuarioRepository.findByEmail("reprograma.horario.invalido@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaFutura(2));
        cita.setMotivo("Cita original");
        Cita guardada = citaService.agendarCita(cita);

        LocalDateTime fueraDeHorario = fechaFutura(3).withHour(21).withMinute(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> citaService.reprogramarCita(guardada.getId(), fueraDeHorario));
        assertThat(ex.getMessage()).isEqualTo("El horario de atención es de 08:00 a 20:00");

        assertThat(citaRepository.findById(guardada.getId()).orElseThrow().getFechaHora())
                .isEqualTo(guardada.getFechaHora());
    }

    @Test
    void reprogramarAUnaHoraValidaConMasDe24HorasDeAnticipacionTieneExito() throws Exception {
        registrarYLoguearCliente("reprograma.horario.valido@test.com");
        Usuario cliente = usuarioRepository.findByEmail("reprograma.horario.valido@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaFutura(2));
        cita.setMotivo("Cita original");
        Cita guardada = citaService.agendarCita(cita);

        LocalDateTime nuevaFecha = fechaFutura(4);
        Cita reprogramada = citaService.reprogramarCita(guardada.getId(), nuevaFecha);
        assertThat(reprogramada.getFechaHora()).isEqualTo(nuevaFecha);
    }

    @Test
    void confirmarCancelarYAtenderFuncionanSobreCitasConFechaYaPasada() throws Exception {
        registrarYLoguearCliente("cita.pasada@test.com");
        Usuario cliente = usuarioRepository.findByEmail("cita.pasada@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        Usuario doctor = doctorSeed();

        // Se crean directamente por repositorio, no via CitaService.agendarCita:
        // la nueva validacion de horario/anticipacion es para AGENDAR y
        // REPROGRAMAR (crear o mover una cita hacia un horario nuevo), no para
        // gestionar citas que ya existen con fecha pasada. Es exactamente como
        // el DataSeeder crea sus citas historicas de demostracion.
        Cita citaPasada = new Cita();
        citaPasada.setMascota(mascota);
        citaPasada.setDoctor(doctor);
        citaPasada.setFechaHora(LocalDateTime.now().minusDays(1).withHour(10).withMinute(0));
        citaPasada.setMotivo("Cita historica a confirmar y cancelar");
        citaPasada.setEstado(EstadoCita.PENDIENTE);
        citaPasada.setFechaCreacion(LocalDateTime.now().minusDays(3));
        citaPasada = citaRepository.save(citaPasada);

        Cita confirmada = citaService.confirmarCita(citaPasada.getId());
        assertThat(confirmada.getEstado()).isEqualTo(EstadoCita.CONFIRMADA);

        Cita cancelada = citaService.cancelarCita(citaPasada.getId());
        assertThat(cancelada.getEstado()).isEqualTo(EstadoCita.CANCELADA);

        Cita otraCitaPasada = new Cita();
        otraCitaPasada.setMascota(mascota);
        otraCitaPasada.setDoctor(doctor);
        otraCitaPasada.setFechaHora(LocalDateTime.now().minusHours(2));
        otraCitaPasada.setMotivo("Cita historica a atender");
        otraCitaPasada.setEstado(EstadoCita.PENDIENTE);
        otraCitaPasada.setFechaCreacion(LocalDateTime.now().minusDays(2));
        otraCitaPasada = citaRepository.save(otraCitaPasada);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", otraCitaPasada.getId()).with(csrf()).session(sesionDoctor)
                        .param("diagnostico", "Revision retroactiva")
                        .param("tratamiento", "Ninguno"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctor/citas?atendida"));

        assertThat(citaRepository.findById(otraCitaPasada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.ATENDIDA);
    }
}
