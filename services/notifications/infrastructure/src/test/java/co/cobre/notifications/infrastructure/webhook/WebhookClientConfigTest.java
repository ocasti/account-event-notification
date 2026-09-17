package co.cobre.notifications.infrastructure.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookClientConfigTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("timeoutConfigurationRows")
    void shouldCreateNonNullRestClientWhenGivenTimeoutConfiguration(
        String rowName, Duration connectTimeout, Duration readTimeout
    ) {
        var props = new WebhookProperties(
            connectTimeout,
            readTimeout,
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var config = new WebhookClientConfig();
        var validator = new WebhookUrlValidator(props);

        var restClient = config.webhookRestClient(props, validator);

        assertThat(restClient).isNotNull();
    }

    private static Stream<Arguments> timeoutConfigurationRows() {
        return Stream.of(
            Arguments.of("one second connect and read timeout", Duration.ofSeconds(1), Duration.ofSeconds(1)),
            Arguments.of("one second connect, five second read timeout", Duration.ofSeconds(1), Duration.ofSeconds(5)),
            Arguments.of("five second connect, ten second read timeout", Duration.ofSeconds(5), Duration.ofSeconds(10))
        );
    }

    @Test
    void shouldInvokeServerOnceWhenResponseIsServiceUnavailable() throws IOException {
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
            } catch (RestClientException ignored) {
            }

            assertThat(counter.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRouteRequestToPinnedAddressWhenHostIsValidated() throws IOException {
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
