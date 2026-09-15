package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.WebhookUrl;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @Test
    void shouldValidateHttpsUrlWithPublicIp() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://api.example.com/hook"));
        var ip = validator.validate(url);

        assertThat(ip.getHostAddress()).isEqualTo("93.184.216.34");
    }

    @Test
    void shouldRejectPrivateIp10Range() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("10.0.0.5");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://private.example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPrivateIp192Range() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("192.168.1.1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://private.example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPrivateIp172Range() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("172.16.0.1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://private.example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectLoopbackAddress() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("127.0.0.1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://localhost.example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMetadataAddress() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("169.254.169.254");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://metadata.example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectIpv6Loopback() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("::1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectIpv6UniqueLocal() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("fd00::1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowPrivateIpWhenHostInAllowlist() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of("wiremock"),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("10.0.0.5");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://wiremock:8080/webhook"));
        var ip = validator.validate(url);

        assertThat(ip.getHostAddress()).isEqualTo("10.0.0.5");
    }


    @Test
    void shouldThrowOnDnsResolutionFailure() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        java.util.function.Function<String, List<InetAddress>> failingResolver = host -> {
            throw new RuntimeException("DNS resolution failed");
        };
        var validator = new WebhookUrlValidator(props, failingResolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(Exception.class);
    }

    private java.util.function.Function<String, List<InetAddress>> createMockResolver(String ipAddress) throws Exception {
        return host -> {
            try {
                return List.of(InetAddress.getByName(ipAddress));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
