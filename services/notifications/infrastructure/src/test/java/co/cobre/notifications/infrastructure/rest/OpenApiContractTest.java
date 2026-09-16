package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.boot.BootTestSupport;
import co.cobre.notifications.infrastructure.security.RestTestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for the OpenAPI document exposed at {@code /v3/api-docs}: verifies the generated
 * spec matches the real API surface (title, the three real paths, the bearer security scheme,
 * the list operation's query parameters, and the status codes each operation declares), and that
 * {@code clientId} (resolved server-side from the JWT via {@code @AuthenticatedClient}) never
 * leaks into it as a parameter. Also writes the spec to {@code target/openapi.json}, from which
 * the versioned copy at {@code docs/api/openapi.json} is derived.
 *
 * <p>Runs with the {@code api} and {@code local} profiles (springdoc is only enabled under
 * {@code local}), reusing the singleton PostgreSQL and ElasticMQ containers from
 * {@link BootTestSupport}. JWT signing uses {@link RestTestSecurityConfig}, whose key pair is
 * registered as the JWT public key for this context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"api", "local"})
class OpenApiContractTest extends BootTestSupport {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String elasticMQEndpoint = String.format("http://localhost:%d", BootTestSupport.ELASTICMQ.getMappedPort(9324));
        registry.add("spring.cloud.aws.sqs.endpoint", () -> elasticMQEndpoint);
        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "local");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "local");
        registry.add("notifications.sqs.queue-name", () -> "account-events-boot");
        registry.add("spring.flyway.placeholders.webhookUrl", () -> "https://example.test/webhook");
        registry.add("notifications.jwt.public-key", () -> "file:" + RestTestSecurityConfig.publicKeyFile());
    }

    @Test
    void apiDocsMatchTheRealApiSurface() throws Exception {
        String jwtToken = RestTestSecurityConfig.token("CLIENT001");

        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode spec = JSON_MAPPER.readTree(body);

        assertThat(spec.path("info").path("title").asString())
            .isEqualTo("Account Event Notification API");

        JsonNode paths = spec.path("paths");
        List<String> pathNames = collectFieldNames(paths);
        assertThat(pathNames).containsExactlyInAnyOrder(
            "/notification_events",
            "/notification_events/{notification_event_id}",
            "/notification_events/{notification_event_id}/replay"
        );

        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("type").asString())
            .isEqualTo("http");
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString())
            .isEqualTo("bearer");
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("bearerFormat").asString())
            .isEqualTo("JWT");

        List<String> globalSecuritySchemeNames = collectFieldNames(spec.path("security").get(0));
        assertThat(globalSecuritySchemeNames).contains("bearerAuth");

        assertThat(collectAllParameterNames(spec)).doesNotContain("clientId");

        JsonNode listParameters = paths.path("/notification_events").path("get").path("parameters");
        List<String> listParameterNames = collectParameterNames(listParameters);
        assertThat(listParameterNames).containsExactlyInAnyOrder(
            "delivery_status", "from", "to", "limit", "cursor"
        );

        JsonNode replayResponses = paths
            .path("/notification_events/{notification_event_id}/replay")
            .path("post")
            .path("responses");
        assertThat(collectFieldNames(replayResponses)).containsExactlyInAnyOrder("202", "401", "404", "409");

        JsonNode listResponses = paths.path("/notification_events").path("get").path("responses");
        assertThat(collectFieldNames(listResponses)).containsExactlyInAnyOrder("200", "400", "401");

        JsonNode getResponses = paths
            .path("/notification_events/{notification_event_id}")
            .path("get")
            .path("responses");
        assertThat(collectFieldNames(getResponses)).containsExactlyInAnyOrder("200", "401", "404");

        writeSpecToTarget(body);
    }

    private void writeSpecToTarget(String body) throws Exception {
        Path target = Path.of("target", "openapi.json");
        Files.createDirectories(target.getParent());
        JsonNode spec = JSON_MAPPER.readTree(body);
        String pretty = JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        Files.writeString(target, pretty, StandardCharsets.UTF_8);
    }

    private List<String> collectFieldNames(JsonNode node) {
        return new java.util.ArrayList<>(node.propertyNames());
    }

    private List<String> collectParameterNames(JsonNode parameters) {
        List<String> names = new java.util.ArrayList<>();
        if (parameters.isArray()) {
            parameters.forEach(p -> names.add(p.path("name").asString()));
        }
        return names;
    }

    /**
     * Walks the whole spec tree and collects every "name" value found under a "parameters"
     * array, regardless of which operation it belongs to.
     */
    private List<String> collectAllParameterNames(JsonNode spec) {
        List<String> names = new java.util.ArrayList<>();
        collectAllParameterNames(spec, names);
        return names;
    }

    private void collectAllParameterNames(JsonNode node, List<String> names) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.propertyStream().toList()) {
                if ("parameters".equals(entry.getKey()) && entry.getValue().isArray()) {
                    entry.getValue().forEach(p -> names.add(p.path("name").asString()));
                } else {
                    collectAllParameterNames(entry.getValue(), names);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectAllParameterNames(child, names));
        }
    }
}
