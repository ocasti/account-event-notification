package co.cobre.notifications.boot;

import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.infrastructure.worker.AccountEventListener;
import co.cobre.notifications.infrastructure.worker.DeliveryScheduler;
import co.cobre.notifications.infrastructure.worker.MeteredNotificationEventRepository;
import co.cobre.notifications.infrastructure.worker.MeteredWebhookSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
 *
 * Uses singleton Testcontainers (PostgreSQL and ElasticMQ) configured in parent class.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.flyway.enabled=true"
)
@AutoConfigureMockMvc
@ActiveProfiles({"worker", "local"})
class WorkerProfileBootIT extends BootTestSupport {

    @Autowired
    private ApplicationContext context;

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
        registry.add("notifications.jwt.public-key", () -> "file:" + BootTestSupport.publicKeyPath);
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
    void webhookSenderIsMetered() {
        WebhookSender sender = context.getBean(WebhookSender.class);
        assertThat(sender).isInstanceOf(MeteredWebhookSender.class);
    }

    @Test
    void notificationEventRepositoryIsMetered() {
        NotificationEventRepository repository = context.getBean(NotificationEventRepository.class);
        assertThat(repository).isInstanceOf(MeteredNotificationEventRepository.class);
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
