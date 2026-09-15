package co.cobre.notifications.boot;

import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import co.cobre.notifications.infrastructure.worker.AccountEventListener;
import co.cobre.notifications.infrastructure.worker.DeliveryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot test for API profile: validates that the application starts correctly
 * with api and local profiles, exposes required endpoints, and has the correct beans.
 * Uses singleton Testcontainers (PostgreSQL and ElasticMQ) configured in parent class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"api", "local"})
class ApiProfileBootIT extends BootTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void actuatorHealthReadinessRespondsWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk());
    }

    @Test
    void notificationEventsEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/notification_events"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accountEventListenerNotRegistered() {
        assertThat(context.getBeansOfType(AccountEventListener.class)).isEmpty();
    }

    @Test
    void deliverySchedulerNotRegistered() {
        assertThat(context.getBeansOfType(DeliveryScheduler.class)).isEmpty();
    }

    @Test
    void allUseCasesRegistered() {
        assertThat(context.getBeansOfType(RegisterNotificationEvent.class)).isNotEmpty();
        assertThat(context.getBeansOfType(ListNotificationEvents.class)).isNotEmpty();
        assertThat(context.getBeansOfType(GetNotificationEvent.class)).isNotEmpty();
        assertThat(context.getBeansOfType(ReplayNotificationEvent.class)).isNotEmpty();
        assertThat(context.getBeansOfType(ProcessDueDeliveries.class)).isNotEmpty();
    }

    @Test
    void flywayMigrationsApplied() {
        Integer subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions",
            Integer.class
        );
        assertThat(subscriptionCount).isEqualTo(3);
    }
}
