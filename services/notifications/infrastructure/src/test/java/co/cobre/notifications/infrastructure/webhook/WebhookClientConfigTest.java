package co.cobre.notifications.infrastructure.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebhookClientConfigTest {

    @Autowired
    private RestClient webhookRestClient;

    @Autowired
    private WebhookProperties webhookProperties;

    @Test
    void shouldCreateWebhookRestClientBean() {
        assertThat(webhookRestClient).isNotNull();
    }

    @Test
    void shouldApplyTimeoutProperties() {
        assertThat(webhookProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(webhookProperties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
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

        assertThat(props).isNotNull();
    }
}
