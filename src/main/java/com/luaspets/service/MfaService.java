package com.luaspets.service;

import com.luaspets.exception.BusinessException;
import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.TotpSecretCipher;
import com.luaspets.security.TotpService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {
    private static final int RECOVERY_CODE_COUNT = 10;
    @PersistenceContext private EntityManager entityManager;
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final TotpSecretCipher cipher;
    private final TotpService totp;
    private final SecureRandom random = new SecureRandom();
    public MfaService(
        UsuarioRepository users, PasswordEncoder passwords, TotpSecretCipher cipher, TotpService totp) {
        this.users = users;
        this.passwords = passwords;
        this.cipher = cipher;
        this.totp = totp;
    }
    private Usuario locked(Long id, long version) {
        // Preserve changes from earlier calls sharing the transaction before refreshing collections.
        entityManager.flush();
        Usuario u = users.findByIdForUpdate(id).orElseThrow(this::invalid);
        // Callers may already hold a managed entity; refresh its state under the lock.
        entityManager.refresh(u, LockModeType.PESSIMISTIC_WRITE);
        if (!Boolean.TRUE.equals(u.getActivo()) || u.getSecurityVersion() != version)
            throw invalid();
        return u;
    }
    private BusinessException invalid() {
        return new BusinessException("El código ingresado no es válido.");
    }
    public boolean passwordValid(Long id, String password) {
        return password != null
            && users.findById(id).filter(u -> passwords.matches(password, u.getPassword())).isPresent();
    }
    @Transactional
    public boolean verify(Long id, long version, String code, boolean recovery) {
        Usuario u = locked(id, version);
        if (!u.isTwoFactorEnabled())
            return false;
        if (recovery) {
            if (code == null || !code.matches("[A-Fa-f0-9]{20}"))
                return false;
            for (int i = 0; i < u.getRecoveryCodeHashes().size(); i++) {
                if (passwords.matches(code.toLowerCase(Locale.ROOT), u.getRecoveryCodeHashes().get(i))) {
                    u.getRecoveryCodeHashes().remove(i);
                    return true;
                }
            }
            return false;
        }
        return acceptTotp(u, code);
    }
    private boolean acceptTotp(Usuario u, String code) {
        Long counter =
            totp.matchingCounter(cipher.decrypt(u.getTwoFactorSecret()), code, u.getTwoFactorLastCounter());
        if (counter == null)
            return false;
        u.setTwoFactorLastCounter(counter);
        return true;
    }
    @Transactional
    public List<String> enable(Long id, long version, String encryptedSecret, String code) {
        Usuario u = locked(id, version);
        if (u.isTwoFactorEnabled())
            throw invalid();
        Long counter = totp.matchingCounter(cipher.decrypt(encryptedSecret), code, null);
        if (counter == null)
            throw invalid();
        u.setTwoFactorSecret(encryptedSecret);
        u.setTwoFactorEnabled(true);
        u.setTwoFactorLastCounter(counter);
        u.setSecurityVersion(u.getSecurityVersion() + 1);
        return replaceRecovery(u);
    }
    @Transactional
    public void disable(Long id, long version, String password, String code) {
        Usuario u = locked(id, version);
        if (u.getRol() == Rol.ADMIN)
            throw new BusinessException("Los administradores deben mantener la verificación en dos pasos.");
        authorizeManagement(u, password, code);
        u.setTwoFactorEnabled(false);
        u.setTwoFactorSecret(null);
        u.setTwoFactorLastCounter(null);
        u.getRecoveryCodeHashes().clear();
        u.setSecurityVersion(u.getSecurityVersion() + 1);
    }
    @Transactional
    public List<String> regenerate(Long id, long version, String password, String code) {
        Usuario u = locked(id, version);
        authorizeManagement(u, password, code);
        u.setSecurityVersion(u.getSecurityVersion() + 1);
        return replaceRecovery(u);
    }
    private void authorizeManagement(Usuario u, String password, String code) {
        if (!u.isTwoFactorEnabled() || password == null || !passwords.matches(password, u.getPassword())
            || !acceptTotp(u, code))
            throw invalid();
    }
    private List<String> replaceRecovery(Usuario u) {
        u.getRecoveryCodeHashes().clear();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            byte[] bytes = new byte[10];
            random.nextBytes(bytes);
            String code = HexFormat.of().formatHex(bytes);
            codes.add(code);
            u.getRecoveryCodeHashes().add(passwords.encode(code));
        }
        return codes;
    }
}
