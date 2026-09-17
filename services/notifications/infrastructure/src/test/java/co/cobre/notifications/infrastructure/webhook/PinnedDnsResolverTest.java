package co.cobre.notifications.infrastructure.webhook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedDnsResolverTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolvableHostRows")
    void shouldResolveSingleAddressWhenHostPassesValidation(
        String rowName, List<String> allowlist, String host, String resolverIp, String expectedIp
    ) throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, allowlist, Duration.ofSeconds(30)
        );
        var mockResolver = createMockResolver(resolverIp);
        var validator = new WebhookUrlValidator(props, mockResolver);
        var dnsResolver = new PinnedDnsResolver(validator);

        var addresses = dnsResolver.resolve(host);

        assertThat(addresses).hasSize(1);
        assertThat(addresses[0].getHostAddress()).isEqualTo(expectedIp);
    }

    private static Stream<Arguments> resolvableHostRows() {
        return Stream.of(
            Arguments.of("public ip without allowlist", List.of(), "example.com", "93.184.216.34", "93.184.216.34"),
            Arguments.of("private ip with host in allowlist", List.of("internal.test"), "internal.test", "10.0.0.5", "10.0.0.5")
        );
    }

    @Test
    void shouldThrowWhenPrivateIpNotAllowlisted() throws Exception {
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
    void shouldReturnHostUnchangedWhenResolvingCanonicalHostname() throws Exception {
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
