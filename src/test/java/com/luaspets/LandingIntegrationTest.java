package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class LandingIntegrationTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    void inicioRenderizaMetadatosYJerarquiaSinPerderElHeadPwa() throws Exception {
        String html = mvc.perform(get("/"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<html lang=\"es\">", "<title>Luas Pets</title>",
            "name=\"description\"", "Luas Pets: atención veterinaria para tu mascota, citas en línea, historial médico digital y una tienda especializada en su cuidado.",
            "href=\"/manifest.webmanifest\"", "src=\"/js/pwa.js\"",
            "srcset=\"/images/hero-480.webp 480w, /images/hero-800.webp 800w, /images/hero-1280.webp 1280w\"",
            "srcset=\"/images/nosotros-480.webp 480w, /images/nosotros-800.webp 800w, /images/nosotros-1280.webp 1280w\"",
            "src=\"/images/hero.jpg\" width=\"2752\" height=\"1536\"",
            "src=\"/images/nosotros.jpg\" width=\"2752\" height=\"1536\" loading=\"lazy\"");
        assertThat(java.util.regex.Pattern.compile("<h1(?:\\s|>)").matcher(html).results().count()).isEqualTo(1);
        assertThat(java.util.regex.Pattern.compile("<h3(?:\\s|>)").matcher(html).results().count()).isEqualTo(3);
        assertThat(html).doesNotContain("<h4", "<h5", "<h6", "th:srcset", "th:replace");
        for (String image : new String[] {"hero", "nosotros"}) {
            for (int width : new int[] {480, 800, 1280}) {
                mvc.perform(get("/images/" + image + "-" + width + ".webp"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/webp"));
            }
        }
    }

    @Test
    void robotsEsTextoPublicoSinRedireccionYLasPaginasPrivadasSiguenProtegidas() throws Exception {
        var response = mvc.perform(get("/robots.txt"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andReturn().getResponse();
        assertThat(response.getContentAsString()).isEqualTo("User-agent: *\nDisallow:\n");
        assertThat(response.getRedirectedUrl()).isNull();
        mvc.perform(get("/cliente/dashboard")).andExpect(redirectedUrl("/login"));
    }
}
