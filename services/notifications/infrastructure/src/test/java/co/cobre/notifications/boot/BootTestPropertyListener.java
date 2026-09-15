package co.cobre.notifications.boot;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListener;

/**
 * Shared test execution listener for boot tests that registers dynamic properties.
 * This ensures ElasticMQ and PostgreSQL properties are available to all boot tests.
 */
public class BootTestPropertyListener implements TestExecutionListener {

    /**
     * Register dynamic properties for boot tests.
     * Configures ElasticMQ endpoint, SQS credentials, JWT properties, webhook properties, and Flyway settings.
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
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
}
