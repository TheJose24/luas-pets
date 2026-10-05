package com.luaspets.security;

import com.luaspets.model.Rol;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final Clock clock;

    public RoleBasedAuthenticationSuccessHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        var user = principal.getUsuario();
        var session = request.getSession();
        session.setAttribute(MfaSession.PASSWORD_CONFIRMED, clock.instant());
        MfaSession.clearSetup(session);
        MfaSession.resetAttempts(session);
        if (user.isTwoFactorEnabled()) {
            session.setAttribute(MfaSession.STATE, MfaSession.State.PENDING);
            response.sendRedirect(request.getContextPath() + "/2fa");
        } else if (user.getRol() == Rol.ADMIN) {
            session.setAttribute(MfaSession.STATE, MfaSession.State.ENROLLMENT);
            response.sendRedirect(request.getContextPath() + "/2fa/configurar");
        } else {
            session.setAttribute(MfaSession.STATE, MfaSession.State.COMPLETE);
            response.sendRedirect(request.getContextPath() + dashboard(user.getRol()));
        }
    }
    public static String dashboard(Rol role) {
        return switch (role) {
            case ADMIN -> "/admin/dashboard";
            case DOCTOR -> "/doctor/dashboard";
            case CLIENTE -> "/cliente/dashboard";
        };
    }
}
