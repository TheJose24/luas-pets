package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;

/**
 * Cubre la pagina de perfil: lectura para los tres roles, actualizacion de
 * datos personales sin tocar email/rol/password, cambio de contrasena con
 * todas sus validaciones, y que ningun endpoint acepte operar sobre un
 * usuario distinto al autenticado (no existe parametro de id en absoluto).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PerfilIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    void getPerfilSinAutenticarRedirigeAlLogin() throws Exception {
        mvc().perform(get("/perfil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void getPerfilResponde200ParaLosTresRoles() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("perfil.cliente@test.com");
        mvc().perform(get("/perfil").session(sesionCliente)).andExpect(status().isOk());

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");
        mvc().perform(get("/perfil").session(sesionDoctor)).andExpect(status().isOk());

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        mvc().perform(get("/perfil").session(sesionAdmin)).andExpect(status().isOk());
    }

    @Test
    void postDatosActualizaNombreApellidoYTelefonoSinTocarEmailRolNiPassword() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("perfil.actualizar@test.com");
        Usuario original = usuarioRepository.findByEmail("perfil.actualizar@test.com").orElseThrow();
        String passwordHashOriginal = original.getPassword();

        mvc().perform(post("/perfil/datos").with(csrf()).session(sesion)
                        .param("nombre", "NombreNuevo")
                        .param("apellido", "ApellidoNuevo")
                        .param("telefono", "912345678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attribute("exito", "Tus datos se actualizaron correctamente."));

        Usuario actualizado = usuarioRepository.findById(original.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("NombreNuevo");
        assertThat(actualizado.getApellido()).isEqualTo("ApellidoNuevo");
        assertThat(actualizado.getTelefono()).isEqualTo("912345678");
        assertThat(actualizado.getEmail()).isEqualTo("perfil.actualizar@test.com");
        assertThat(actualizado.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(actualizado.getPassword()).isEqualTo(passwordHashOriginal);
        assertThat(actualizado.getActivo()).isTrue();

        // Verifica que el SecurityContext de la MISMA sesion ya refleja los
        // datos nuevos sin volver a iniciar sesion: el topbar (fragments/layout
        // :: topbar) renderiza el nombre/apellido con sec:authentication, es
        // decir, directamente desde el principal que vive en la sesion, no
        // desde el Model de este controlador (que siempre relee de la BD). Si
        // PerfilController no reemplazara el Authentication, el topbar
        // seguiria mostrando "DePrueba" (el apellido con el que este usuario
        // de prueba se registro) en vez de "ApellidoNuevo". "DePrueba" no
        // aparece en ningun otro lugar de la pagina, asi que su ausencia aqui
        // confirma que el principal de la sesion ya es el actualizado.
        String html = mvc().perform(get("/perfil").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("NombreNuevo").contains("ApellidoNuevo").doesNotContain("DePrueba");
    }

    @Test
    void postDatosConNombreEnBlancoFallaYNoAlteraLosDatos() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("perfil.nombreblanco@test.com");
        Usuario original = usuarioRepository.findByEmail("perfil.nombreblanco@test.com").orElseThrow();

        mvc().perform(post("/perfil/datos").with(csrf()).session(sesion)
                        .param("nombre", "   ")
                        .param("apellido", "ApellidoNuevo")
                        .param("telefono", "912345678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attribute("error", "El nombre y el apellido son obligatorios"));

        Usuario sinCambios = usuarioRepository.findById(original.getId()).orElseThrow();
        assertThat(sinCambios.getNombre()).isEqualTo(original.getNombre());
        assertThat(sinCambios.getApellido()).isEqualTo(original.getApellido());
        assertThat(sinCambios.getTelefono()).isEqualTo(original.getTelefono());
    }

    @Test
    void postPasswordConActualIncorrectaFallaYNoCambiaLaPassword() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("perfil.passwordincorrecta@test.com");
        Usuario original = usuarioRepository.findByEmail("perfil.passwordincorrecta@test.com").orElseThrow();
        String hashOriginal = original.getPassword();

        mvc().perform(post("/perfil/password").with(csrf()).session(sesion)
                        .param("passwordActual", "claveIncorrecta")
                        .param("passwordNueva", "nuevaClave123")
                        .param("passwordConfirmacion", "nuevaClave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attribute("errorPassword", "La contraseña actual no es correcta"));

        assertThat(usuarioRepository.findById(original.getId()).orElseThrow().getPassword()).isEqualTo(hashOriginal);
    }

    @Test
    void postPasswordConNuevaDeMenosDe8CaracteresFalla() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("perfil.passwordcorta@test.com");
        Usuario original = usuarioRepository.findByEmail("perfil.passwordcorta@test.com").orElseThrow();
        String hashOriginal = original.getPassword();

        mvc().perform(post("/perfil/password").with(csrf()).session(sesion)
                        .param("passwordActual", "clave123")
                        .param("passwordNueva", "abc123")
                        .param("passwordConfirmacion", "abc123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attribute("errorPassword", "La nueva contraseña debe tener al menos 8 caracteres"));

        assertThat(usuarioRepository.findById(original.getId()).orElseThrow().getPassword()).isEqualTo(hashOriginal);
    }

    @Test
    void postPasswordConNuevaYConfirmacionDistintasFalla() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("perfil.passwordnocoincide@test.com");
        Usuario original = usuarioRepository.findByEmail("perfil.passwordnocoincide@test.com").orElseThrow();
        String hashOriginal = original.getPassword();

        mvc().perform(post("/perfil/password").with(csrf()).session(sesion)
                        .param("passwordActual", "clave123")
                        .param("passwordNueva", "nuevaClave123")
                        .param("passwordConfirmacion", "otraClave456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attribute("errorPassword", "Las contraseñas nuevas no coinciden"));

        assertThat(usuarioRepository.findById(original.getId()).orElseThrow().getPassword()).isEqualTo(hashOriginal);
    }

    @Test
    void postPasswordConDatosValidosCambiaLaPasswordYPermiteLoginSoloConLaNueva() throws Exception {
        MockHttpSession sesion = registrarYLoguearCliente("perfil.passwordvalida@test.com");

        mvc().perform(post("/perfil/password").with(csrf()).session(sesion)
                        .param("passwordActual", "clave123")
                        .param("passwordNueva", "nuevaClave123")
                        .param("passwordConfirmacion", "nuevaClave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reauth"));

        assertThat(sesion.isInvalid()).isTrue();

        Usuario actualizado = usuarioRepository.findByEmail("perfil.passwordvalida@test.com").orElseThrow();
        assertThat(passwordEncoder.matches("nuevaClave123", actualizado.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("clave123", actualizado.getPassword())).isFalse();

        MockHttpSession sesionConNueva = new MockHttpSession();
        mvc().perform(post("/login").with(csrf()).session(sesionConNueva)
                        .param("username", "perfil.passwordvalida@test.com")
                        .param("password", "nuevaClave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/dashboard"));

        MockHttpSession sesionConVieja = new MockHttpSession();
        mvc().perform(post("/login").with(csrf()).session(sesionConVieja)
                        .param("username", "perfil.passwordvalida@test.com")
                        .param("password", "clave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unUsuarioNoPuedeModificarElPerfilDeOtroAunquePaseUnIdPorParametro() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("perfil.usuarioA@test.com");
        Usuario usuarioA = usuarioRepository.findByEmail("perfil.usuarioA@test.com").orElseThrow();

        registrarYLoguearCliente("perfil.usuarioB@test.com");
        Usuario usuarioB = usuarioRepository.findByEmail("perfil.usuarioB@test.com").orElseThrow();
        String nombreOriginalDeB = usuarioB.getNombre();

        // Los endpoints de perfil no declaran ningun parametro de id: aunque se
        // intente colar uno (como haria un atacante manipulando la peticion),
        // Spring simplemente lo ignora porque el metodo del controlador no lo
        // recibe. La operacion siempre actua sobre el usuario del
        // SecurityContext de la sesion (usuarioA en este caso), nunca sobre el
        // id que se intente pasar.
        mvc().perform(post("/perfil/datos").with(csrf()).session(sesionA)
                        .param("nombre", "IntentoDeAtaque")
                        .param("apellido", "Malicioso")
                        .param("telefono", "900000000")
                        .param("id", usuarioB.getId().toString())
                        .param("usuarioId", usuarioB.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"));

        Usuario aActualizado = usuarioRepository.findById(usuarioA.getId()).orElseThrow();
        assertThat(aActualizado.getNombre()).isEqualTo("IntentoDeAtaque");

        Usuario bSinCambios = usuarioRepository.findById(usuarioB.getId()).orElseThrow();
        assertThat(bSinCambios.getNombre()).isEqualTo(nombreOriginalDeB);
    }
}
