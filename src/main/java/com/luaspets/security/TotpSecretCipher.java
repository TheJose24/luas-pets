package com.luaspets.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TotpSecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public TotpSecretCipher(@Value("${luaspets.totp.encryption-key:}") String configuredKey) {
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredKey);
            if (bytes.length != 32)
                throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY debe ser una clave Base64 de 32 bytes.");
        }
    }
    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD("luaspets-totp-v1".getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP.");
        }
    }
    public String decrypt(String encrypted) {
        try {
            if (encrypted == null || !encrypted.startsWith("v1:"))
                throw new IllegalArgumentException();
            byte[] envelope = Base64.getDecoder().decode(encrypted.substring(3));
            if (envelope.length < 28)
                throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope, 0, 12));
            cipher.updateAAD("luaspets-totp-v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(envelope, 12, envelope.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer la configuración TOTP.");
        }
    }
}
