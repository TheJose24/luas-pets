package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.model.Cita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.UsuarioRepository;

/**
 * Cubre la descarga del expediente clinico en PDF: mismas reglas de acceso
 * que las vistas equivalentes (propiedad para el cliente, haber atendido al
 * menos una vez para el doctor), y que el archivo devuelto sea realmente un
 * PDF valido incluso cuando el paciente no tiene consultas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpedientePdfIntegrationTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final byte[] FIRMA_PDF = { '%', 'P', 'D', 'F' };

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private CitaRepository citaRepository;

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
        mascota.setNombre("Cortaúñas");
        mascota.setEspecie("Gato");
        mascota.setCliente(cliente);
        return mascotaRepository.save(mascota);
    }

    // Hora fija (14:00, no 10:00) y 2 dias de adelanto: evita el limite de
    // 24h de CitaService.validarHorario y no choca con la cita que
    // DataSeeder siembra para doctor@luaspets.com en dia+3 a las 10:00 (ver
    // CitaIntegrationTest para el mismo razonamiento).
    private String fechaFuturaTexto() {
        return LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0).format(FORMAT);
    }

    private void atenderMascotaComoDoctor(Mascota mascota, MockHttpSession sesionCliente) throws Exception {
        Usuario doctor = usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();

        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                .param("mascotaId", mascota.getId().toString())
                .param("doctorId", doctor.getId().toString())
                .param("fechaHora", fechaFuturaTexto())
                .param("motivo", "Consulta para expediente"));

        Cita cita = citaRepository.findByMascotaIdOrderByFechaHoraDesc(mascota.getId()).get(0);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(post("/doctor/citas/{id}/atender", cita.getId()).with(csrf()).session(sesionDoctor)
                .param("diagnostico", "Diagnóstico de prueba con vacunación")
                .param("tratamiento", "Tratamiento con antibiótico"));
    }

    @Test
    void clienteDescargaElExpedienteDeSuPropiaMascota() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("pdf.cliente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("pdf.cliente@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        atenderMascotaComoDoctor(mascota, sesion);

        var resultado = mvc().perform(get("/cliente/mascotas/{id}/expediente.pdf", mascota.getId()).session(sesion))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] cuerpo = resultado.getResponse().getContentAsByteArray();
        assertThat(cuerpo.length).isGreaterThan(0);
        assertThat(java.util.Arrays.copyOf(cuerpo, 4)).isEqualTo(FIRMA_PDF);
    }

    @Test
    void clienteRecibe403AlPedirElExpedienteDeUnaMascotaAjena() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("pdf.propietarioA@test.com");
        Usuario clienteA = usuarioRepository.findByEmail("pdf.propietarioA@test.com").orElseThrow();
        Mascota mascotaDeA = crearMascotaPara(clienteA);

        MockHttpSession sesionB = registrarYLoguearCliente("pdf.intrusoB@test.com");

        mvc().perform(get("/cliente/mascotas/{id}/expediente.pdf", mascotaDeA.getId()).session(sesionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorDescargaElExpedienteDeUnPacienteQueHaAtendido() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("pdf.paciente.atendido@test.com");
        Usuario cliente = usuarioRepository.findByEmail("pdf.paciente.atendido@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        atenderMascotaComoDoctor(mascota, sesionCliente);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        var resultado = mvc()
                .perform(get("/doctor/pacientes/{id}/expediente.pdf", mascota.getId()).session(sesionDoctor))
                .andExpect(status().isOk())
                .andReturn();

        byte[] cuerpo = resultado.getResponse().getContentAsByteArray();
        assertThat(cuerpo.length).isGreaterThan(0);
        assertThat(java.util.Arrays.copyOf(cuerpo, 4)).isEqualTo(FIRMA_PDF);
    }

    @Test
    void doctorRecibe403AlPedirElExpedienteDeUnaMascotaQueNuncaHaAtendido() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("pdf.paciente.ajeno@test.com");
        Usuario cliente = usuarioRepository.findByEmail("pdf.paciente.ajeno@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(get("/doctor/pacientes/{id}/expediente.pdf", mascota.getId()).session(sesionDoctor))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioSinAutenticarEsRedirigidoAlLogin() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("pdf.sinauth@test.com");
        Usuario cliente = usuarioRepository.findByEmail("pdf.sinauth@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);

        mvc().perform(get("/cliente/mascotas/{id}/expediente.pdf", mascota.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login"));
    }

    @Test
    void unAdminNoPuedeAccederANingunEndpointDeExpedientePdf() throws Exception {
        // Restriccion critica explicita: el ADMIN no tiene acceso a fichas
        // clinicas. No es una regla nueva especifica de estos endpoints, sino
        // consecuencia de que ambos cuelgan de prefijos ("/cliente/**" y
        // "/doctor/**") que SecurityConfig ya restringe por rol; se verifica
        // igual, dado que es una restriccion critica senalada aparte.
        MockHttpSession sesionCliente = registrarYLoguearCliente("pdf.paraadmin@test.com");
        Usuario cliente = usuarioRepository.findByEmail("pdf.paraadmin@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        atenderMascotaComoDoctor(mascota, sesionCliente);

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/cliente/mascotas/{id}/expediente.pdf", mascota.getId()).session(sesionAdmin))
                .andExpect(status().isForbidden());

        mvc().perform(get("/doctor/pacientes/{id}/expediente.pdf", mascota.getId()).session(sesionAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaMascotaSinConsultasGeneraIgualmenteUnPdfValido() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("pdf.sinconsultas@test.com");
        Usuario cliente = usuarioRepository.findByEmail("pdf.sinconsultas@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);
        // Sin citas ni historiales: el expediente solo tiene los datos del
        // paciente, sin nada en "Historial clinico".

        var resultado = mvc().perform(get("/cliente/mascotas/{id}/expediente.pdf", mascota.getId()).session(sesion))
                .andExpect(status().isOk())
                .andReturn();

        byte[] cuerpo = resultado.getResponse().getContentAsByteArray();
        assertThat(cuerpo.length).isGreaterThan(0);
        assertThat(java.util.Arrays.copyOf(cuerpo, 4)).isEqualTo(FIRMA_PDF);
    }
}
