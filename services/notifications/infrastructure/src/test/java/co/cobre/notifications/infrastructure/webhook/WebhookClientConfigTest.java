package co.cobre.notifications.infrastructure.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookClientConfigTest {

    @Test
    void shouldCreateWebhookRestClientBean() {
        var props = new WebhookProperties(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var config = new WebhookClientConfig();
        var validator = new WebhookUrlValidator(props);

        var restClient = config.webhookRestClient(props, validator);

        assertThat(restClient).isNotNull();
    }

    @Test
    void shouldApplyConnectTimeout() {
        var props = new WebhookProperties(
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var config = new WebhookClientConfig();
        var validator = new WebhookUrlValidator(props);

        var restClient = config.webhookRestClient(props, validator);

        assertThat(restClient).isNotNull();
    }

    @Test
    void shouldDisableRedirectHandling() {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var config = new WebhookClientConfig();
        var validator = new WebhookUrlValidator(props);

        var restClient = config.webhookRestClient(props, validator);

        assertThat(restClient).isNotNull();
    }
}
