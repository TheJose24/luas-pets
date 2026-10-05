package com.luaspets.security;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

@Service
public class TotpService {
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Base32 base32 = new Base32();
    private final TimeBasedOneTimePasswordGenerator generator =
        new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    public TotpService(Clock clock) {
        this.clock = clock;
    }
    public String generateSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32.encodeToString(bytes).replace("=", "");
    }
    public String uri(String secret, String email) {
        return "otpauth://totp/" + encode("LUAS Pets:" + email) + "?secret=" + secret
            + "&issuer=" + encode("LUAS Pets") + "&algorithm=SHA1&digits=6&period=30";
    }
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    public Long matchingCounter(String secret, String code, Long lastCounter) {
        if (code == null || !code.matches("[0-9]{6}"))
            return null;
        long current = clock.instant().getEpochSecond() / 30;
        Long match = null;
        for (long counter = current - 1; counter <= current + 1; counter++) {
            if (lastCounter != null && counter <= lastCounter)
                continue;
            try {
                String expected = generator.generateOneTimePasswordString(
                    new SecretKeySpec(base32.decode(secret), "HmacSHA1"), Instant.ofEpochSecond(counter * 30),
                    Locale.ROOT);
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                        code.getBytes(StandardCharsets.US_ASCII)))
                    match = counter;
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo verificar el código.");
            }
        }
        return match;
    }
}
