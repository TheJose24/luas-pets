package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.TotpSecretCipher;
import com.luaspets.security.TotpService;
import com.luaspets.service.MfaService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Real committed rows and independent request transactions exercise pessimistic locks. */
@SpringBootTest
@ActiveProfiles("test")
@Import(MfaIntegrationTest.TimeConfiguration.class)
class MfaConcurrencyTest {
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
    PlatformTransactionManager manager;
    @Autowired
    MfaIntegrationTest.MutableClock clock;

    record Fixture(Long id, String email, String secret, List<String> codes) {}

    @Test
    void totpYRecuperacionSoloConcedenUnaSesionEnPeticionesParalelas() throws Exception {
        clock.now = Instant.parse("2026-10-05T12:00:00Z");
        MockMvc mvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .apply(
                                org.springframework.security.test.web.servlet.setup
                                        .SecurityMockMvcConfigurers.springSecurity())
                        .build();
        TransactionTemplate tx = new TransactionTemplate(manager);
        for (boolean recovery : new boolean[] {false, true}) {
            String secret = totp.generateSecret();
            String previous = MfaTestSupport.code(secret, clock.instant().minusSeconds(30));
            Fixture fixture =
                    tx.execute(
                            status -> {
                                Usuario u = new Usuario();
                                u.setNombre("Carrera");
                                u.setApellido("MFA");
                                u.setEmail("parallel-" + recovery + "@test.com");
                                u.setPassword(passwords.encode("clave123"));
                                u.setRol(Rol.CLIENTE);
                                u.setActivo(true);
                                u.setFechaRegistro(LocalDateTime.now());
                                users.saveAndFlush(u);
                                List<String> codes =
                                        mfa.enable(
                                                u.getId(),
                                                u.getSecurityVersion(),
                                                cipher.encrypt(secret),
                                                previous);
                                return new Fixture(u.getId(), u.getEmail(), secret, codes);
                            });
            try {
                MockHttpSession first = login(mvc, fixture.email());
                MockHttpSession second = login(mvc, fixture.email());
                String code =
                        recovery
                                ? fixture.codes().getFirst()
                                : MfaTestSupport.code(secret, clock.instant());
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                    Callable<Boolean> request1 = request(mvc, first, code, recovery, ready, start);
                    Callable<Boolean> request2 = request(mvc, second, code, recovery, ready, start);
                    Future<Boolean> a = executor.submit(request1);
                    Future<Boolean> b = executor.submit(request2);
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                            .containsExactlyInAnyOrder(true, false);
                }
            } finally {
                tx.executeWithoutResult(status -> users.deleteById(fixture.id()));
            }
        }
    }

    private MockHttpSession login(MockMvc mvc, String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(
                        post("/login")
                                .with(csrf())
                                .session(session)
                                .param("username", email)
                                .param("password", "clave123"))
                .andExpect(redirectedUrl("/2fa"));
        return session;
    }

    private Callable<Boolean> request(
            MockMvc mvc,
            MockHttpSession session,
            String code,
            boolean recovery,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Race did not start");
            var response =
                    mvc.perform(
                                    post("/2fa")
                                            .with(csrf())
                                            .session(session)
                                            .param("codigo", code)
                                            .param("recuperacion", Boolean.toString(recovery)))
                            .andReturn()
                            .getResponse();
            return "/cliente/dashboard".equals(response.getRedirectedUrl());
        };
    }
}
