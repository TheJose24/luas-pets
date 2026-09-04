package com.luaspets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Cubre: registro + login exitoso, login fallido, acceso sin autenticar,
 * y bloqueo por rol (403) para un CLIENTE en rutas de doctor/admin.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    @Test
    void registroYLoginExitoso() throws Exception {
        mvc().perform(post("/registro").with(csrf())
                        .param("nombre", "Ana")
                        .param("apellido", "Torres")
                        .param("email", "ana.torres@test.com")
                        .param("password", "clave123")
                        .param("confirmPassword", "clave123")
                        .param("telefono", "987654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registrado"));

        mvc().perform(post("/login").with(csrf())
                        .param("username", "ana.torres@test.com")
                        .param("password", "clave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/dashboard"));
    }

    @Test
    void loginFallidoConCredencialesIncorrectas() throws Exception {
        mvc().perform(post("/registro").with(csrf())
                .param("nombre", "Bruno")
                .param("apellido", "Diaz")
                .param("email", "bruno.diaz@test.com")
                .param("password", "clave123")
                .param("confirmPassword", "clave123")
                .param("telefono", "987654322"));

        mvc().perform(post("/login").with(csrf())
                        .param("username", "bruno.diaz@test.com")
                        .param("password", "claveIncorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void usuarioNoAutenticadoEsRedirigidoAlLoginEnRutaProtegida() throws Exception {
        mvc().perform(get("/cliente/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void clienteRecibe403EnDashboardAdminYEnCitasDeDoctor() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc().perform(post("/registro").with(csrf()).session(session)
                .param("nombre", "Carla")
                .param("apellido", "Ruiz")
                .param("email", "carla.ruiz@test.com")
                .param("password", "clave123")
                .param("confirmPassword", "clave123")
                .param("telefono", "987654323"));
        mvc().perform(post("/login").with(csrf()).session(session)
                .param("username", "carla.ruiz@test.com")
                .param("password", "clave123"));

        mvc().perform(get("/admin/dashboard").session(session))
                .andExpect(status().isForbidden());

        mvc().perform(get("/doctor/citas").session(session))
                .andExpect(status().isForbidden());
    }
}
