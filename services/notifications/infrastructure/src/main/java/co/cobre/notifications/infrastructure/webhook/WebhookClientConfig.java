package co.cobre.notifications.infrastructure.webhook;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the webhook RestClient.
 * Uses Apache HttpClient 5 with configured timeouts, no redirects, and DNS validation.
 */
@Configuration
public class WebhookClientConfig {

    /**
     * Creates a RestClient for sending webhooks with Apache HttpClient 5.
     * No redirects allowed, custom timeout handling, DNS resolver integration.
     */
    @Bean
    public RestClient webhookRestClient(WebhookProperties props, WebhookUrlValidator validator) {
        throw new UnsupportedOperationException("not implemented");
    }
}
