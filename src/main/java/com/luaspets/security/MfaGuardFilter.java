package com.luaspets.security;

import com.luaspets.model.Rol;
import com.luaspets.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Checks the database security stamp before any authenticated application route. */
public class MfaGuardFilter extends OncePerRequestFilter {
    private final UsuarioRepository users;
    public MfaGuardFilter(UsuarioRepository users) {
        this.users = users;
    }
    private static final Set<String> PUBLIC =
        Set.of("/", "/login", "/registro", "/health", "/error", "/logout",
            "/manifest.webmanifest", "/sw.js", "/offline.html");
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
            var user = users.findById(principal.getUsuario().getId()).orElse(null);
            if (user == null || !Boolean.TRUE.equals(user.getActivo())
                || user.getSecurityVersion() != principal.getSecurityVersion()) {
                expire(request, response);
                return;
            }
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean publicPath = PUBLIC.contains(path) || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/webjars/") || path.startsWith("/icons/");
            var session = request.getSession(false);
            Object state = session == null ? null : session.getAttribute(MfaSession.STATE);
            String required = user.isTwoFactorEnabled() ? "/2fa"
                : user.getRol() == Rol.ADMIN            ? "/2fa/configurar"
                                                        : null;
            if (required != null && state != MfaSession.State.COMPLETE && !publicPath) {
                boolean allowed = required.equals("/2fa")
                    ? path.equals("/2fa")
                    : path.equals("/2fa/configurar") || path.equals("/2fa/activar");
                if (!allowed) {
                    response.sendRedirect(request.getContextPath() + required);
                    return;
                }
            }
        }
        if (authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)
            && !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            expire(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
    public static void expire(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null)
            request.getSession(false).invalidate();
        response.sendRedirect(request.getContextPath() + "/login?reauth");
    }
}
