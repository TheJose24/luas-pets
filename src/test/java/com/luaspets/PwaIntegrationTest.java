package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PwaIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UsuarioRepository users;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    void recursosPwaSonPublicosYNoRedirigenAlLogin() throws Exception {
        var manifest = mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk())
            .andReturn().getResponse();
        assertThat(manifest.getContentType()).startsWith("application/manifest+json");
        assertThat(manifest.getContentAsString()).contains("\"scope\": \"/\"", "\"display\": \"standalone\"");
        var sw = mvc.perform(get("/sw.js")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(sw.getContentType()).contains("javascript");
        assertThat(sw.getContentAsString()).contains("luas-pets-pwa-", "SENSITIVE_PATH");
        mvc.perform(get("/offline.html")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/js/pwa.js")).andExpect(status().isOk());
    }

    @Test
    void soloLosRecursosEstaticosExplicitosTienenCachePublica() throws Exception {
        for (String path : List.of("/css/luaspets.css", "/js/carrito.js", "/js/notificaciones.js", "/js/pwa.js",
                "/icons/icon-192.png", "/icons/icon-512.png", "/icons/icon-maskable-512.png", "/icons/apple-touch-icon.png")) {
            var response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse();
            assertThat(response.getHeader("Cache-Control")).contains("public", "max-age=0", "must-revalidate")
                .doesNotContain("private", "no-store");
            if (path.endsWith(".png")) {
                assertThat(response.getContentType()).isEqualTo("image/png");
                var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
                int size = path.contains("192") ? 192 : path.contains("apple") ? 180 : 512;
                assertThat(image.getWidth()).isEqualTo(size);
                assertThat(image.getHeight()).isEqualTo(size);
                assertThat(image.getRGB(0, 0)).isEqualTo(0xff0f766e);
            }
        }
        for (String path : List.of("/", "/login", "/sw.js", "/manifest.webmanifest", "/offline.html",
                "/images/login.jpg", "/admin/dashboard", "/doctor/dashboard", "/cliente/dashboard", "/perfil", "/2fa")) {
            var response = mvc.perform(get(path)).andReturn().getResponse();
            assertThat(response.getHeader("Cache-Control")).contains("no-store").doesNotContain("public");
        }
    }

    @Test
    void headComunRegistraWorkerRaizEnInicioYLogin() throws Exception {
        for (String path : List.of("/", "/login")) {
            String html = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("/manifest.webmanifest", "/js/pwa.js", "#0f766e", "/icons/icon-192.png", "/icons/apple-touch-icon.png");
        }
        String script = mvc.perform(get("/js/pwa.js")).andReturn().getResponse().getContentAsString();
        assertThat(script).contains("register('/sw.js'", "scope: '/'", "updateViaCache: 'none'");
    }

    @Test
    void recursosPublicosDuranteMfaSinPermitirPaginasPrivadas() throws Exception {
        Usuario admin = new Usuario();
        admin.setNombre("PWA"); admin.setApellido("Prueba"); admin.setEmail("pwa@test.com");
        admin.setPassword(passwords.encode("clave123")); admin.setRol(Rol.ADMIN);
        admin.setActivo(true); admin.setFechaRegistro(LocalDateTime.now());
        users.saveAndFlush(admin);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").with(csrf()).session(session)
                .param("username", admin.getEmail()).param("password", "clave123"))
            .andExpect(redirectedUrl("/2fa/configurar"));
        for (String path : List.of("/manifest.webmanifest", "/sw.js", "/offline.html", "/js/pwa.js", "/icons/icon-192.png", "/icons/icon-512.png",
                "/icons/icon-maskable-512.png", "/icons/apple-touch-icon.png"))
            mvc.perform(get(path).session(session)).andExpect(status().isOk());
        // A nonexistent icon must be 404 rather than a redirect to MFA.
        mvc.perform(get("/icons/missing.png").session(session)).andExpect(status().isNotFound());
        for (String path : List.of("/admin/dashboard", "/doctor/dashboard", "/cliente/dashboard", "/perfil"))
            mvc.perform(get(path).session(session)).andExpect(redirectedUrl("/2fa/configurar"));
        String html = mvc.perform(get("/2fa/configurar").session(session))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/js/pwa.js");
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(csrf()).session(session)).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        for (String path : List.of("/admin/dashboard", "/doctor/dashboard", "/cliente/dashboard", "/perfil", "/2fa"))
            mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
    }
}
