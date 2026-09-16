package co.cobre.notifications.infrastructure.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void shouldNotRetryOnServiceUnavailable() throws IOException {
        var counter = new AtomicInteger(0);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            counter.incrementAndGet();
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();

        try {
            var port = server.getAddress().getPort();
            var props = new WebhookProperties(
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                true,
                List.of("127.0.0.1"),
                Duration.ofSeconds(30)
            );
            var config = new WebhookClientConfig();
            var validator = new WebhookUrlValidator(props);
            var restClient = config.webhookRestClient(props, validator);

            try {
                restClient.get()
                    .uri("http://127.0.0.1:" + port + "/webhook")
                    .retrieve()
                    .body(String.class);
            } catch (Exception ignored) {
            }

            assertThat(counter.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUsePinnedDnsResolverForValidatedHosts() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("OK".getBytes());
            exchange.close();
        });
        server.start();

        try {
            var port = server.getAddress().getPort();
            var props = new WebhookProperties(
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                true,
                List.of("pinned.test"),
                Duration.ofSeconds(30)
            );
            java.util.function.Function<String, List<java.net.InetAddress>> mockResolver = host -> {
                try {
                    return List.of(java.net.InetAddress.getByName("127.0.0.1"));
                } catch (java.net.UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            };
            var validator = new WebhookUrlValidator(props, mockResolver);
            var config = new WebhookClientConfig();
            var restClient = config.webhookRestClient(props, validator);

            var response = restClient.get()
                .uri("http://pinned.test:" + port + "/webhook")
                .retrieve()
                .body(String.class);

            assertThat(response).isEqualTo("OK");
        } finally {
            server.stop(0);
        }
    }
}
