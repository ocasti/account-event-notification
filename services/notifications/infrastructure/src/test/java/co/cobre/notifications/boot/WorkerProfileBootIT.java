package co.cobre.notifications.boot;

import co.cobre.notifications.infrastructure.messaging.AccountEventListener;
import co.cobre.notifications.infrastructure.scheduler.DeliveryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot test for worker profile: validates that the application starts correctly
 * with worker and local profiles, has worker-specific beans, and exposes metrics endpoints.
 *
 * Note: spring.flyway.enabled=true is set in this test even though the worker profile
 * does not migrate in production. This is necessary for tests to populate the database schema.
 * The configuration is overridden via test properties.
 *
 * Uses singleton Testcontainers (PostgreSQL and ElasticMQ) configured in parent class.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.flyway.enabled=true",
        "notifications.webhook.connect-timeout=5s",
        "notifications.webhook.read-timeout=10s",
        "notifications.webhook.timestamp-tolerance=300s"
    }
)
@ActiveProfiles({"worker", "local"})
@Import(BootTestConfiguration.class)
class WorkerProfileBootIT extends BootTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerBootProperties(DynamicPropertyRegistry registry) {
        String elasticMQEndpoint = String.format("http://localhost:%d", BootTestSupport.ELASTICMQ.getMappedPort(9324));
        registry.add("spring.cloud.aws.sqs.endpoint", () -> elasticMQEndpoint);
        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "local");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "local");
        registry.add("notifications.sqs.queue-name", () -> "account-events-boot");
        registry.add("spring.flyway.placeholders.webhookUrl", () -> "https://example.test/webhook");
        registry.add("notifications.jwt.audience", () -> "account-event-notification");
        registry.add("notifications.jwt.client-claim", () -> "sub");
        registry.add("notifications.jwt.public-key", () -> "file:" + BootTestSupport.publicKeyPath);
        registry.add("notifications.webhook.allowlist", () -> "example.test,localhost");
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void accountEventListenerRegistered() {
        assertThat(context.getBeansOfType(AccountEventListener.class)).isNotEmpty();
    }

    @Test
    void deliverySchedulerRegistered() {
        assertThat(context.getBeansOfType(DeliveryScheduler.class)).isNotEmpty();
    }

    @Test
    void actuatorHealthReadinessResponds() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheusRespondsWithNotificationsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("notifications_")));
    }
}
