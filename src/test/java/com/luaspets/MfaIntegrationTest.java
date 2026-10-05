package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.MfaSession;
import com.luaspets.security.TotpSecretCipher;
import com.luaspets.security.TotpService;
import com.luaspets.service.MfaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Import(MfaIntegrationTest.TimeConfiguration.class)
@Transactional
class MfaIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String PASSWORD = "clave123";
    @Autowired
    WebApplicationContext context;
    @Autowired
    UsuarioRepository users;
    @Autowired
    PasswordEncoder passwords;
    @Autowired
    TotpService totp;
    @Autowired
    TotpSecretCipher cipher;
    @Autowired
    MfaService mfa;
    @Autowired
    MutableClock clock;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach
    void initialize() {
        clock.now = NOW;
        mvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .apply(
                                org.springframework.security.test.web.servlet.setup
                                        .SecurityMockMvcConfigurers.springSecurity())
                        .build();
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        Instant now = NOW;

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    record Account(Usuario user, String secret, List<String> recovery) {}

    private Usuario user(Rol role) {
        Usuario u = new Usuario();
        u.setNombre("Prueba");
        u.setApellido("MFA");
        u.setEmail("mfa-" + role + "@test.com");
        u.setPassword(passwords.encode(PASSWORD));
        u.setRol(role);
        u.setActivo(true);
        u.setFechaRegistro(LocalDateTime.now());
        return users.saveAndFlush(u);
    }

    private Account enabled(Rol role) throws Exception {
        Usuario u = user(role);
        String secret = totp.generateSecret();
        List<String> codes =
                mfa.enable(
                        u.getId(),
                        u.getSecurityVersion(),
                        cipher.encrypt(secret),
                        MfaTestSupport.code(secret, clock.instant().minusSeconds(30)));
        users.flush();
        return new Account(u, secret, codes);
    }

    private MockHttpSession login(Usuario user, String redirect) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(
                        post("/login")
                                .with(csrf())
                                .session(session)
                                .param("username", user.getEmail())
                                .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(redirect));
        return session;
    }

    private String token(String secret) throws Exception {
        return MfaTestSupport.code(secret, clock.instant());
    }

    private void recoveryLogin(MockHttpSession session, String code, String destination)
            throws Exception {
        mvc.perform(
                        post("/2fa")
                                .with(csrf())
                                .session(session)
                                .param("codigo", code)
                                .param("recuperacion", "true"))
                .andExpect(redirectedUrl(destination));
    }

    private String startSetup(MockHttpSession session) throws Exception {
        mvc.perform(
                        post("/2fa/configurar")
                                .with(csrf())
                                .session(session)
                                .param("passwordActual", PASSWORD))
                .andExpect(status().isOk());
        return cipher.decrypt((String) session.getAttribute(MfaSession.PROVISIONAL));
    }

    @Test
    void insertarCuentaSinColumnasMfaUsaDefaultsCompatibles() {
        jdbc.update(
                "insert into usuario (nombre,apellido,email,password,rol,activo,fecha_registro)"
                    + " values (?,?,?,?,?,?,?)",
                "Existente",
                "Migracion",
                "legacy-default@test.com",
                passwords.encode(PASSWORD),
                "CLIENTE",
                true,
                LocalDateTime.now());
        Usuario u = users.findByEmail("legacy-default@test.com").orElseThrow();
        assertThat(u.isTwoFactorEnabled()).isFalse();
        assertThat(u.getSecurityVersion()).isZero();
        assertThat(u.getTwoFactorSecret()).isNull();
        assertThat(u.getTwoFactorLastCounter()).isNull();
        assertThat(u.getRecoveryCodeHashes()).isEmpty();
    }

    @Test
    void clienteYDoctorSinMfaConservanLoginNormal() throws Exception {
        for (Rol role : List.of(Rol.CLIENTE, Rol.DOCTOR)) {
            Usuario u = user(role);
            String route = "/" + role.name().toLowerCase() + "/dashboard";
            mvc.perform(get(route).session(login(u, route))).andExpect(status().isOk());
        }
    }

    @Test
    void passwordCorrectaNoPermiteBypassDeNingunaRutaProtegidaParaTodosLosRoles() throws Exception {
        for (Rol role : Rol.values()) {
            Account account = enabled(role);
            MockHttpSession session = login(account.user(), "/2fa");
            for (String route :
                    List.of(
                            "/cliente/dashboard",
                            "/doctor/dashboard",
                            "/admin/dashboard",
                            "/perfil",
                            "/notificaciones",
                            "/cualquier-ruta-autenticada"))
                mvc.perform(get(route).session(session)).andExpect(redirectedUrl("/2fa"));
            mvc.perform(
                            post("/perfil/datos")
                                    .with(csrf())
                                    .session(session)
                                    .param("nombre", "Bypass")
                                    .param("apellido", "Falla"))
                    .andExpect(redirectedUrl("/2fa"));
            assertThat(account.user().getNombre()).isEqualTo("Prueba");
        }
    }

    @Test
    void totpIncorrectoNoConcedeAccesoYCorrectoCompletaLaSesion() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        MockHttpSession session = login(account.user(), "/2fa");
        mvc.perform(post("/2fa").with(csrf()).session(session).param("codigo", "abcdef"))
                .andExpect(status().isOk());
        mvc.perform(get("/cliente/dashboard").session(session)).andExpect(redirectedUrl("/2fa"));
        mvc.perform(
                        post("/2fa")
                                .with(csrf())
                                .session(session)
                                .param("codigo", token(account.secret())))
                .andExpect(redirectedUrl("/cliente/dashboard"));
        assertThat(session.getAttribute(MfaSession.STATE)).isEqualTo(MfaSession.State.COMPLETE);
        mvc.perform(get("/perfil").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin/dashboard").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void registrarParametrosDeSeguridadNoActivaNiElevaCuenta() throws Exception {
        mvc.perform(
                        post("/registro")
                                .with(csrf())
                                .param("nombre", "Ataque")
                                .param("apellido", "Registro")
                                .param("email", "inyeccion@test.com")
                                .param("password", PASSWORD)
                                .param("confirmPassword", PASSWORD)
                                .param("rol", "ADMIN")
                                .param("twoFactorEnabled", "true")
                                .param("twoFactorSecret", "secret")
                                .param("securityVersion", "999")
                                .param("twoFactorLastCounter", "999"))
                .andExpect(redirectedUrl("/login?registrado"));
        Usuario u = users.findByEmail("inyeccion@test.com").orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(u.isTwoFactorEnabled()).isFalse();
        assertThat(u.getTwoFactorSecret()).isNull();
        assertThat(u.getSecurityVersion()).isZero();
    }

    @Test
    void generarQrYEnviarCodigoInvalidoNoPersisteSecreto() throws Exception {
        Usuario u = user(Rol.CLIENTE);
        MockHttpSession session = login(u, "/cliente/dashboard");
        String secret = startSetup(session);
        assertThat(secret).isNotBlank();
        assertThat(u.isTwoFactorEnabled()).isFalse();
        assertThat(u.getTwoFactorSecret()).isNull();
        mvc.perform(post("/2fa/activar").with(csrf()).session(session).param("codigo", "invalid"))
                .andExpect(status().isOk());
        assertThat(users.findById(u.getId()).orElseThrow().isTwoFactorEnabled()).isFalse();
        mvc.perform(get("/cliente/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    void confirmarSetupCifraSecretoMuestraRecoveryUnaVezYRevocaSesiones() throws Exception {
        Usuario u = user(Rol.CLIENTE);
        MockHttpSession other = login(u, "/cliente/dashboard");
        MockHttpSession session = login(u, "/cliente/dashboard");
        String secret = startSetup(session);
        MvcResult result =
                mvc.perform(
                                post("/2fa/activar")
                                        .with(csrf())
                                        .session(session)
                                        .param("codigo", token(secret)))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(u.isTwoFactorEnabled()).isTrue();
        assertThat(u.getTwoFactorSecret()).doesNotContain(secret);
        assertThat(cipher.decrypt(u.getTwoFactorSecret())).isEqualTo(secret);
        assertThat(u.getRecoveryCodeHashes()).hasSize(10).allMatch(hash -> hash.startsWith("$2"));
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Copia estos códigos ahora", "se muestran únicamente en esta respuesta");
        assertThat(session.getAttribute(MfaSession.PROVISIONAL)).isNull();
        mvc.perform(get("/perfil").session(other)).andExpect(redirectedUrl("/login?reauth"));
        mvc.perform(get("/perfil").session(session)).andExpect(redirectedUrl("/login?reauth"));
    }

    @Test
    void administradorExistenteDebeConfigurarAntesDeDashboardSinBucle() throws Exception {
        Usuario u = user(Rol.ADMIN);
        MockHttpSession session = login(u, "/2fa/configurar");
        for (String route : List.of("/admin/dashboard", "/perfil", "/2fa", "/doctor/dashboard"))
            mvc.perform(get(route).session(session)).andExpect(redirectedUrl("/2fa/configurar"));
        mvc.perform(get("/2fa/configurar").session(session)).andExpect(status().isOk());
        String secret = cipher.decrypt((String) session.getAttribute(MfaSession.PROVISIONAL));
        mvc.perform(
                        post("/2fa/activar")
                                .with(csrf())
                                .session(session)
                                .param("codigo", token(secret)))
                .andExpect(status().isOk());
        clock.now = NOW.plusSeconds(30);
        MockHttpSession newSession = login(u, "/2fa");
        mvc.perform(post("/2fa").with(csrf()).session(newSession).param("codigo", token(secret)))
                .andExpect(redirectedUrl("/admin/dashboard"));
        mvc.perform(get("/admin/dashboard").session(newSession)).andExpect(status().isOk());
        clock.now = NOW.plusSeconds(60);
        mvc.perform(
                        post("/2fa/desactivar")
                                .with(csrf())
                                .session(newSession)
                                .param("passwordActual", PASSWORD)
                                .param("codigo", token(secret)))
                .andExpect(status().is3xxRedirection());
        assertThat(u.isTwoFactorEnabled()).isTrue();
    }

    @Test
    void desactivarRequierePasswordYTOTPActualYEliminaRecuperacion() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        Usuario u = account.user();
        MockHttpSession session = login(u, "/2fa");
        recoveryLogin(session, account.recovery().getFirst(), "/cliente/dashboard");
        mvc.perform(
                        post("/2fa/desactivar")
                                .with(csrf())
                                .session(session)
                                .param("passwordActual", "bad")
                                .param("codigo", token(account.secret())))
                .andExpect(status().is3xxRedirection());
        assertThat(u.isTwoFactorEnabled()).isTrue();
        mvc.perform(
                        post("/2fa/desactivar")
                                .with(csrf())
                                .session(session)
                                .param("passwordActual", PASSWORD)
                                .param("codigo", "invalid"))
                .andExpect(status().is3xxRedirection());
        assertThat(u.isTwoFactorEnabled()).isTrue();
        mvc.perform(
                        post("/2fa/desactivar")
                                .with(csrf())
                                .session(session)
                                .param("passwordActual", PASSWORD)
                                .param("codigo", token(account.secret())))
                .andExpect(status().is3xxRedirection());
        assertThat(u.isTwoFactorEnabled()).isFalse();
        assertThat(u.getTwoFactorSecret()).isNull();
        assertThat(u.getRecoveryCodeHashes()).isEmpty();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void codigoRecuperacionSoloSeUsaUnaVezYRegenerarInvalidaTodos() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        Usuario u = account.user();
        long version = u.getSecurityVersion();
        assertThat(mfa.verify(u.getId(), version, account.recovery().getFirst(), true)).isTrue();
        assertThat(mfa.verify(u.getId(), version, account.recovery().getFirst(), true)).isFalse();
        List<String> replacement =
                mfa.regenerate(u.getId(), version, PASSWORD, token(account.secret()));
        assertThat(replacement).hasSize(10).doesNotContainAnyElementsOf(account.recovery());
        for (String old : account.recovery())
            assertThat(mfa.verify(u.getId(), u.getSecurityVersion(), old, true)).isFalse();
        assertThat(mfa.verify(u.getId(), u.getSecurityVersion(), replacement.getFirst(), true))
                .isTrue();
    }

    @Test
    void recuperacionCompletaLoginYNoSeAlmacenaEnTextoPlano() throws Exception {
        Account account = enabled(Rol.DOCTOR);
        MockHttpSession session = login(account.user(), "/2fa");
        assertThat(account.user().getRecoveryCodeHashes()).noneMatch(account.recovery()::contains);
        recoveryLogin(session, account.recovery().getFirst(), "/doctor/dashboard");
        mvc.perform(get("/doctor/dashboard").session(session)).andExpect(status().isOk());
        MockHttpSession again = login(account.user(), "/2fa");
        mvc.perform(
                        post("/2fa")
                                .with(csrf())
                                .session(again)
                                .param("codigo", account.recovery().getFirst())
                                .param("recuperacion", "true"))
                .andExpect(status().isOk());
        mvc.perform(get("/doctor/dashboard").session(again)).andExpect(redirectedUrl("/2fa"));
    }

    @Test
    void csrfSigueObligatorioEnTodosLosPostMfa() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        MockHttpSession session = login(account.user(), "/2fa");
        for (String route :
                List.of(
                        "/2fa",
                        "/2fa/configurar",
                        "/2fa/activar",
                        "/2fa/desactivar",
                        "/2fa/recuperacion"))
            mvc.perform(
                            post(route)
                                    .session(session)
                                    .param("codigo", token(account.secret()))
                                    .param("passwordActual", PASSWORD))
                    .andExpect(status().isForbidden());
        assertThat(account.user().isTwoFactorEnabled()).isTrue();
    }

    @Test
    void logoutInvalidaContextoYEstadoMfa() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        MockHttpSession session = login(account.user(), "/2fa");
        recoveryLogin(session, account.recovery().getFirst(), "/cliente/dashboard");
        mvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/perfil")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void demasiadosIntentosInvalidanProcesoYNoAceptanCodigoPosterior() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        MockHttpSession session = login(account.user(), "/2fa");
        for (int i = 0; i < 6 && !session.isInvalid(); i++)
            mvc.perform(post("/2fa").with(csrf()).session(session).param("codigo", "invalid"));
        assertThat(session.isInvalid()).isTrue();
        assertThat(account.user().getTwoFactorLastCounter())
                .isEqualTo(NOW.getEpochSecond() / 30 - 1);
    }

    @Test
    void setupProvisionalCaducaSinActivarBD() throws Exception {
        Usuario u = user(Rol.CLIENTE);
        MockHttpSession session = login(u, "/cliente/dashboard");
        String secret = startSetup(session);
        clock.now = NOW.plusSeconds(601);
        mvc.perform(
                post("/2fa/activar").with(csrf()).session(session).param("codigo", token(secret)));
        assertThat(u.isTwoFactorEnabled()).isFalse();
        assertThat(u.getTwoFactorSecret()).isNull();
    }

    @Test
    void adminConPasswordAntiguaDebeReconfirmarlaAlCaducarSetup() throws Exception {
        Usuario u = user(Rol.ADMIN);
        MockHttpSession session = login(u, "/2fa/configurar");
        mvc.perform(get("/2fa/configurar").session(session)).andExpect(status().isOk());
        String oldSecret = cipher.decrypt((String) session.getAttribute(MfaSession.PROVISIONAL));
        clock.now = NOW.plusSeconds(601);
        mvc.perform(get("/2fa/configurar").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("2fa/iniciar"));
        mvc.perform(
                        post("/2fa/activar")
                                .with(csrf())
                                .session(session)
                                .param("codigo", token(oldSecret)))
                .andExpect(redirectedUrl("/2fa/configurar"));
        assertThat(u.isTwoFactorEnabled()).isFalse();
        String newSecret = startSetup(session);
        assertThat(newSecret).isNotEqualTo(oldSecret);
        mvc.perform(
                        post("/2fa/activar")
                                .with(csrf())
                                .session(session)
                                .param("codigo", token(newSecret)))
                .andExpect(status().isOk());
        assertThat(u.isTwoFactorEnabled()).isTrue();
    }

    @Test
    void totpAceptadoNoSeReutilizaEntreSesiones() throws Exception {
        Account account = enabled(Rol.CLIENTE);
        String code = token(account.secret());
        MockHttpSession first = login(account.user(), "/2fa");
        mvc.perform(post("/2fa").with(csrf()).session(first).param("codigo", code))
                .andExpect(redirectedUrl("/cliente/dashboard"));
        MockHttpSession second = login(account.user(), "/2fa");
        mvc.perform(post("/2fa").with(csrf()).session(second).param("codigo", code))
                .andExpect(status().isOk());
        mvc.perform(get("/perfil").session(second)).andExpect(redirectedUrl("/2fa"));
    }

    @Test
    void cambioPasswordRevocaLasOtrasSesiones() throws Exception {
        Usuario u = user(Rol.CLIENTE);
        MockHttpSession first = login(u, "/cliente/dashboard");
        MockHttpSession second = login(u, "/cliente/dashboard");
        mvc.perform(
                        post("/perfil/password")
                                .with(csrf())
                                .session(first)
                                .param("passwordActual", PASSWORD)
                                .param("passwordNueva", "nuevaClave123")
                                .param("passwordConfirmacion", "nuevaClave123"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/perfil").session(second)).andExpect(redirectedUrl("/login?reauth"));
    }
}
