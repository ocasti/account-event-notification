package co.cobre.notifications.boot;

import co.cobre.notifications.infrastructure.webhook.WebhookProperties;
import co.cobre.notifications.infrastructure.webhook.WebhookUrlValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

/**
 * Test configuration that provides default webhook properties and validator for boot tests.
 */
@TestConfiguration
public class BootTestConfiguration {

    @Bean
    @Primary
    WebhookProperties webhookProperties() {
        return new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            false,
            List.of("example.test", "localhost", "wiremock"),
            Duration.ofSeconds(300)
        );
    }

    @Bean
    @Primary
    WebhookUrlValidator webhookUrlValidator(WebhookProperties props) {
        return new WebhookUrlValidator(props);
    }
}
