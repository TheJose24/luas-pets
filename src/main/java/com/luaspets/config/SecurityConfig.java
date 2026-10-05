package com.luaspets.config;

import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.CustomUserDetailsService;
import com.luaspets.security.MfaGuardFilter;
import com.luaspets.security.RoleBasedAuthenticationSuccessHandler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public Clock mfaClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
        CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
        RoleBasedAuthenticationSuccessHandler successHandler, UsuarioRepository users) throws Exception {
        http.addFilterBefore(new MfaGuardFilter(users), AuthorizationFilter.class);
        http.authorizeHttpRequests(auth
                -> auth
                    .requestMatchers("/", "/registro", "/login", "/error", "/health", "/css/**", "/js/**",
                        "/images/**", "/webjars/**")
                    .permitAll()
                    .requestMatchers("/cliente/**")
                    .hasRole("CLIENTE")
                    .requestMatchers("/doctor/**")
                    .hasRole("DOCTOR")
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
            .formLogin(form
                -> form.loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(successHandler)
                    .failureUrl("/login?error"))
            .logout(logout
                -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").invalidateHttpSession(true));

        return http.build();
    }
}
