package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.model.EstadoCita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.service.NotificacionService;

/**
 * Verifica la regla critica de robustez: si NotificacionService falla, la
 * operacion de negocio (agendar una cita) debe completarse igual. Se
 * reemplaza NotificacionService por un mock que siempre lanza una excepcion
 * al crear una notificacion, en un contexto de Spring dedicado a esta clase.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificacionFalloNoRompeCitaTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private CitaRepository citaRepository;

    @MockitoBean
    private NotificacionService notificacionService;

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

    @Test
    void siNotificacionServiceFallaLaCitaSeAgendaIgual() throws Exception {
        doThrow(new RuntimeException("Fallo simulado de notificaciones"))
                .when(notificacionService)
                .crear(any(), any(), anyString(), anyString(), anyString());

        MockHttpSession sesionCliente = registrarYLoguearCliente("notif.fallo@test.com");
        Usuario cliente = usuarioRepository.findByEmail("notif.fallo@test.com").orElseThrow();

        Mascota mascota = new Mascota();
        mascota.setNombre("Rocky");
        mascota.setEspecie("Perro");
        mascota.setCliente(cliente);
        mascota = mascotaRepository.save(mascota);

        Usuario doctor = usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();
        String fecha = LocalDateTime.now().plusDays(1).format(FORMAT);

        // El agendamiento no debe fallar aunque el mock de NotificacionService
        // lance una excepcion en cada llamada a crear(...); CitaService la
        // captura internamente y solo la registra en el log.
        mvc().perform(post("/cliente/citas/nueva").with(csrf()).session(sesionCliente)
                        .param("mascotaId", mascota.getId().toString())
                        .param("doctorId", doctor.getId().toString())
                        .param("fechaHora", fecha)
                        .param("motivo", "Chequeo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/citas?exito"));

        var citas = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId());
        assertThat(citas).hasSize(1);
        assertThat(citas.get(0).getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }
}
