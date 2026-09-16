package co.cobre.notifications.infrastructure.webhook;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedDnsResolverTest {

    @Test
    void shouldResolvePublicIpAddress() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var mockResolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, mockResolver);
        var dnsResolver = new PinnedDnsResolver(validator);

        var addresses = dnsResolver.resolve("example.com");

        assertThat(addresses.length).isEqualTo(1);
        assertThat(addresses[0].getHostAddress()).isEqualTo("93.184.216.34");
    }

    @Test
    void shouldRejectPrivateIpWithoutAllowlist() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var mockResolver = createMockResolver("10.0.0.5");
        var validator = new WebhookUrlValidator(props, mockResolver);
        var dnsResolver = new PinnedDnsResolver(validator);

        assertThatThrownBy(() -> dnsResolver.resolve("private.test"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowPrivateIpWhenHostInAllowlist() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of("internal.test"),
            Duration.ofSeconds(30)
        );
        var mockResolver = createMockResolver("10.0.0.5");
        var validator = new WebhookUrlValidator(props, mockResolver);
        var dnsResolver = new PinnedDnsResolver(validator);

        var addresses = dnsResolver.resolve("internal.test");

        assertThat(addresses.length).isEqualTo(1);
        assertThat(addresses[0].getHostAddress()).isEqualTo("10.0.0.5");
    }

    @Test
    void shouldPreserveCanonicalHostname() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of(),
            Duration.ofSeconds(30)
        );
        var mockResolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, mockResolver);
        var dnsResolver = new PinnedDnsResolver(validator);

        var canonical = dnsResolver.resolveCanonicalHostname("example.com");

        assertThat(canonical).isEqualTo("example.com");
    }

    private Function<String, List<InetAddress>> createMockResolver(String ipAddress) throws Exception {
        return host -> {
            try {
                return List.of(InetAddress.getByName(ipAddress));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
