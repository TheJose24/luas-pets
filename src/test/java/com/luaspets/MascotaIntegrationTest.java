package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.UsuarioRepository;

/**
 * Cubre: registro de una mascota y su asociacion correcta al cliente logueado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MascotaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

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

    private Mascota crearMascotaPara(Usuario cliente) {
        Mascota mascota = new Mascota();
        mascota.setNombre("Firulais");
        mascota.setEspecie("Perro");
        mascota.setCliente(cliente);
        return mascotaRepository.save(mascota);
    }

    @Test
    void registrarMascotaQuedaPersistidaYAsociadaAlClienteCorrecto() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("dueño.mascota@test.com");

        mvc().perform(post("/cliente/mascotas/nueva").with(csrf()).session(session)
                        .param("nombre", "Firulais")
                        .param("especie", "Perro")
                        .param("raza", "Mestizo")
                        .param("peso", "12.5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/mascotas?exito"));

        Usuario cliente = usuarioRepository.findByEmail("dueño.mascota@test.com").orElseThrow();
        List<Mascota> mascotas = mascotaRepository.findByClienteId(cliente.getId());

        assertThat(mascotas).hasSize(1);
        assertThat(mascotas.get(0).getNombre()).isEqualTo("Firulais");
        assertThat(mascotas.get(0).getEspecie()).isEqualTo("Perro");
        assertThat(mascotas.get(0).getCliente().getId()).isEqualTo(cliente.getId());
    }

    @Test
    void clienteAccedeASuPropioPerfilDeMascota() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("dueño.perfil@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.perfil@test.com").orElseThrow();
        Mascota mascota = crearMascotaPara(cliente);

        mvc().perform(get("/cliente/mascotas/{id}", mascota.getId()).session(session))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeAccederAlPerfilDeMascotaDeOtroCliente() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("propietarioA.mascota@test.com");
        Usuario clienteA = usuarioRepository.findByEmail("propietarioA.mascota@test.com").orElseThrow();
        Mascota mascotaDeA = crearMascotaPara(clienteA);

        MockHttpSession sesionB = registrarYLoguearCliente("intrusoB.mascota@test.com");

        mvc().perform(get("/cliente/mascotas/{id}", mascotaDeA.getId()).session(sesionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardDelClienteSeRenderizaCorrectamente() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("dashboard.cliente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dashboard.cliente@test.com").orElseThrow();
        crearMascotaPara(cliente);

        mvc().perform(get("/cliente/dashboard").session(session))
                .andExpect(status().isOk());
    }
}
