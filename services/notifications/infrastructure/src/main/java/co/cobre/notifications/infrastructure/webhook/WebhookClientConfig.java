package co.cobre.notifications.infrastructure.webhook;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the webhook RestClient.
 * Uses Apache HttpClient 5 with configured timeouts, no redirects, and DNS validation.
 */
@Configuration
public class WebhookClientConfig {

    @Bean
    public RestClient webhookRestClient(WebhookProperties props, WebhookUrlValidator validator) {
        var connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(props.connectTimeout().toMillis()))
            .setSocketTimeout(Timeout.ofMilliseconds(props.readTimeout().toMillis()))
            .build();

        var connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultConnectionConfig(connectionConfig);

        var httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .disableRedirectHandling()
            .build();

        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
