package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luaspets.security.TotpSecretCipher;
import com.luaspets.security.TotpService;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

class TotpSecurityTest {
    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final Instant NOW = Instant.ofEpochSecond(1234567890L);
    private final TotpService totp = new TotpService(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aesGcmRandomNonceRoundTripAndNoPlaintext() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);
        String first = cipher.encrypt(SECRET);
        assertThat(first).startsWith("v1:").doesNotContain(SECRET);
        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo(SECRET);
    }

    @Test
    void aesGcmRejectsTamperingWrongKeyAndBadEnvelope() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);
        byte[] envelope = Base64.getDecoder().decode(cipher.encrypt(SECRET).substring(3));
        envelope[envelope.length - 1] ^= 1;
        assertThatThrownBy(
                        () -> cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(envelope)))
                .isInstanceOf(IllegalStateException.class);
        String ciphertext = cipher.encrypt(SECRET);
        TotpSecretCipher other =
                new TotpSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        assertThatThrownBy(() -> other.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
        for (String bad : new String[] {SECRET, "v1:bad", "v1:AAAA"})
            assertThatThrownBy(() -> cipher.decrypt(bad)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationRejectsMissingMalformedAndWrongLengthKey() {
        for (String bad : new String[] {"", "not-base64!", "YWJj"})
            assertThatThrownBy(() -> new TotpSecretCipher(bad))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOTP_ENCRYPTION_KEY");
    }

    @Test
    void rfc6238Sha1VectorAndSixDigitFormat() {
        // RFC 6238 Appendix B: SHA1 at 1234567890 = 89005924 (last six digits).
        assertThat(totp.matchingCounter(SECRET, "005924", null)).isEqualTo(41152263L);
        for (String bad : new String[] {"", "12345", "1234567", "abcdef", " 005924", "005924 "})
            assertThat(totp.matchingCounter(SECRET, bad, null)).isNull();
        assertThat(totp.matchingCounter(SECRET, null, null)).isNull();
    }

    @Test
    void acceptsOnlyAdjacentIntervalsAndRejectsReplay() throws Exception {
        long counter = NOW.getEpochSecond() / 30;
        for (int delta : new int[] {-1, 0, 1})
            assertThat(
                            totp.matchingCounter(
                                    SECRET,
                                    MfaTestSupport.code(SECRET, NOW.plusSeconds(delta * 30L)),
                                    null))
                    .isEqualTo(counter + delta);
        for (int delta : new int[] {-2, 2})
            assertThat(
                            totp.matchingCounter(
                                    SECRET,
                                    MfaTestSupport.code(SECRET, NOW.plusSeconds(delta * 30L)),
                                    null))
                    .isNull();
        assertThat(totp.matchingCounter(SECRET, MfaTestSupport.code(SECRET, NOW), counter))
                .isNull();
        assertThat(
                        totp.matchingCounter(
                                SECRET, MfaTestSupport.code(SECRET, NOW.minusSeconds(30)), counter))
                .isNull();
    }

    @Test
    void secretHasEntropyAndUriEncodesIdentity() {
        String secret = totp.generateSecret();
        assertThat(secret).matches("[A-Z2-7]{32}").isNotEqualTo(totp.generateSecret());
        assertThat(totp.uri(secret, "a+b@example.com"))
                .contains(
                        "otpauth://totp/LUAS%20Pets%3Aa%2Bb%40example.com",
                        "issuer=LUAS%20Pets",
                        "algorithm=SHA1",
                        "digits=6",
                        "period=30",
                        "secret=" + secret);
    }
}
