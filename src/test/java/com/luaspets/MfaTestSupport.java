package com.luaspets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.TotpSecretCipher;
import com.luaspets.security.TotpService;

import org.apache.commons.codec.binary.Base32;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.spec.SecretKeySpec;

/** Fixtures authenticate through the real password + TOTP filter chain. */
final class MfaTestSupport {
    private MfaTestSupport() {}

    static String code(String secret, Instant instant) throws Exception {
        return new TimeBasedOneTimePasswordGenerator()
                .generateOneTimePasswordString(
                        new SecretKeySpec(new Base32().decode(secret), "HmacSHA1"),
                        instant,
                        Locale.ROOT);
    }

    static MockHttpSession adminLogin(
            MockMvc mvc, WebApplicationContext context, String email, String password)
            throws Exception {
        UsuarioRepository users = context.getBean(UsuarioRepository.class);
        Usuario admin = users.findByEmail(email).orElseThrow();
        String secret = context.getBean(TotpService.class).generateSecret();
        admin.setTwoFactorEnabled(true);
        admin.setTwoFactorSecret(context.getBean(TotpSecretCipher.class).encrypt(secret));
        admin.setTwoFactorLastCounter(null);
        users.saveAndFlush(admin);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(
                        post("/login")
                                .with(csrf())
                                .session(session)
                                .param("username", email)
                                .param("password", password))
                .andExpect(redirectedUrl("/2fa"));
        mvc.perform(
                        post("/2fa")
                                .with(csrf())
                                .session(session)
                                .param(
                                        "codigo",
                                        code(secret, context.getBean(Clock.class).instant())))
                .andExpect(redirectedUrl("/admin/dashboard"));
        return session;
    }
}
