package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles({"prod", "test"})
class ProductionObservabilityIntegrationTest {
    @Value("${local.server.port}") int applicationPort;
    @Value("${local.management.port}") int managementPort;
    final HttpClient client = HttpClient.newHttpClient();

    @Test
    void productionConfigUses9091AndMetricsAreServedOnASeparateListener() throws Exception {
        Properties production = new Properties();
        try (var input = getClass().getResourceAsStream("/application-prod.properties")) {
            production.load(input);
        }
        assertThat(production.getProperty("management.server.port")).isEqualTo("9091");
        assertThat(managementPort).isNotEqualTo(applicationPort);
        // Generate real application traffic before scraping HTTP request counters.
        assertThat(get(applicationPort, "/health").statusCode()).isEqualTo(200);
        var metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.headers().firstValue("Content-Type").orElseThrow()).contains("text/plain");
        assertThat(metrics.body()).contains("jvm_memory_used_bytes", "process_uptime_seconds", "http_server_requests_seconds_count");
        var health = get(managementPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(get(managementPort, "/actuator/info").statusCode()).isEqualTo(200);
        var legacyHealth = get(applicationPort, "/health");
        assertThat(legacyHealth.statusCode()).isEqualTo(200);
        assertThat(legacyHealth.body()).isEqualTo("OK");
        for (String endpoint : List.of("health", "info", "prometheus")) {
            assertThat(get(applicationPort, "/actuator/" + endpoint).statusCode())
                .as("application listener: %s", endpoint).isNotEqualTo(200);
        }
    }

    @Test
    void productionListenerDoesNotExposeUnneededEndpoints() throws Exception {
        for (String endpoint : List.of("env", "beans", "configprops", "metrics", "loggers", "mappings",
                "heapdump", "threaddump", "shutdown", "scheduledtasks")) {
            assertThat(get(managementPort, "/actuator/" + endpoint).statusCode())
                .as("management listener: %s", endpoint).isNotEqualTo(200);
        }
        assertThat(get(managementPort, "/actuator").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
