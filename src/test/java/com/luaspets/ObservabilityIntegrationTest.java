package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.CustomUserDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ObservabilityIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired WebEndpointsSupplier endpoints;
    @Autowired UsuarioRepository users;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void monitoringIsAvailableWithoutApplicationLogin() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    @Test
    @Transactional
    void pendingAdminMfaDoesNotRedirectMonitoringToApplicationLogin() throws Exception {
        Usuario admin = new Usuario();
        admin.setNombre("Monitoring");
        admin.setApellido("Test");
        admin.setEmail("monitoring@test.com");
        admin.setPassword("unused");
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin.setFechaRegistro(LocalDateTime.now());
        users.saveAndFlush(admin);
        var principal = new CustomUserDetails(admin);
        mvc.perform(get("/actuator/prometheus")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal)))
            .andExpect(status().isOk());
    }

    @Test
    void onlyRequiredEndpointsHaveHttpMappings() throws Exception {
        assertThat(endpoints.getEndpoints()).extracting(endpoint -> endpoint.getEndpointId().toString())
            .containsExactlyInAnyOrder("health", "info", "prometheus");
        for (String endpoint : List.of("env", "beans", "configprops", "metrics", "loggers", "mappings",
                "heapdump", "threaddump", "shutdown", "scheduledtasks")) {
            var response = mvc.perform(get("/actuator/" + endpoint)).andReturn().getResponse();
            assertThat(response.getStatus()).as(endpoint).isNotEqualTo(200);
        }
        mvc.perform(get("/actuator")).andExpect(status().isForbidden());
    }
}
