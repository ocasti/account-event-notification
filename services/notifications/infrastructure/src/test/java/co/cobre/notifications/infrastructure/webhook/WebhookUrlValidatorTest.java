package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.WebhookUrl;
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

    @Test
    void shouldAllowHttpWhenNotRequiredAndHostInAllowlist() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            false,
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
    void shouldAcceptHttpsWhenRequireHttpsIsTrue() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of("example.com"),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));
        var ip = validator.validate(url);

        assertThat(ip.getHostAddress()).isEqualTo("93.184.216.34");
    }

    @Test
    void shouldRejectHostThatDoesNotResolve() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var validator = new WebhookUrlValidator(props);

        var url = new WebhookUrl(new java.net.URI("https://does-not-exist.invalid/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Webhook host does not resolve: does-not-exist.invalid");
    }

    @Test
    void shouldRejectIpv6UniqueLocalFc00() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("fc00::1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Webhook URL resolves to a unique local address");
    }

    @Test
    void shouldRejectIpv6UniqueLocalFd12() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("fd12::1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Webhook URL resolves to a unique local address");
    }

    @Test
    void shouldAcceptIpv6DocumentationAddress() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("2001:db8::1");
        var validator = new WebhookUrlValidator(props, resolver);

        var url = new WebhookUrl(new java.net.URI("https://example.com/hook"));
        var ip = validator.validate(url);

        assertThat(ip.getHostAddress()).contains("2001:db8");
    }

    @Test
    void shouldRejectUriWithoutHost() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        var uriWithoutHost = java.net.URI.create("mailto:test@example.com");

        assertThatThrownBy(() -> validator.validate(uriWithoutHost))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Webhook URL must have a valid host");
    }

    @Test
    void shouldRejectHttpWhenHttpsIsRequired() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        var uri = java.net.URI.create("http://example.com/hook");

        assertThatThrownBy(() -> validator.validate(uri))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("HTTPS is required for webhook URLs");
    }

    @Test
    void shouldRejectHttpHostNotInAllowlistWhenHttpsNotRequired() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            false,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        var uri = java.net.URI.create("http://example.com/hook");

        assertThatThrownBy(() -> validator.validate(uri))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("HTTP scheme requires host to be in allowlist");
    }

    @Test
    void shouldRejectNullHostInValidateHost() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        assertThatThrownBy(() -> validator.validateHost(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Host must not be null or empty");
    }

    @Test
    void shouldRejectEmptyHostInValidateHost() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        assertThatThrownBy(() -> validator.validateHost(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Host must not be null or empty");
    }

    @Test
    void shouldRejectHostWhenResolverReturnsNoAddresses() {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        java.util.function.Function<String, List<InetAddress>> emptyResolver = host -> List.of();
        var validator = new WebhookUrlValidator(props, emptyResolver);

        assertThatThrownBy(() -> validator.validateHost("example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unable to resolve host: example.com");
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
