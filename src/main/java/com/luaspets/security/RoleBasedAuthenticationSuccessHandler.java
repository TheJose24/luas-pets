package com.luaspets.security;

import java.io.IOException;
import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        String redirectUrl;
        if (contieneRol(authorities, "ROLE_ADMIN")) {
            redirectUrl = "/admin/dashboard";
        } else if (contieneRol(authorities, "ROLE_DOCTOR")) {
            redirectUrl = "/doctor/dashboard";
        } else if (contieneRol(authorities, "ROLE_CLIENTE")) {
            redirectUrl = "/cliente/dashboard";
        } else {
            redirectUrl = "/";
        }

        response.sendRedirect(redirectUrl);
    }

    private boolean contieneRol(Collection<? extends GrantedAuthority> authorities, String rol) {
        return authorities.stream().anyMatch(authority -> authority.getAuthority().equals(rol));
    }
}
