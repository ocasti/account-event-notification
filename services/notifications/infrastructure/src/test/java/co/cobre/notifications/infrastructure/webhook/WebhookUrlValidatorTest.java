package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.WebhookUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedAddressRows")
    void shouldResolveAddressWhenUrlPassesValidation(
        String rowName, boolean requireHttps, List<String> allowlist, String urlString,
        String resolverIp, String expectedIpFragment
    ) throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), requireHttps, allowlist, Duration.ofSeconds(30)
        );
        var resolver = createMockResolver(resolverIp);
        var validator = new WebhookUrlValidator(props, resolver);
        var url = new WebhookUrl(new URI(urlString));

        var ip = validator.validate(url);

        assertThat(ip.getHostAddress()).contains(expectedIpFragment);
    }

    private static Stream<Arguments> acceptedAddressRows() {
        return Stream.of(
            Arguments.of(
                "public ip with empty allowlist", true, List.of(),
                "https://api.example.com/hook", "93.184.216.34", "93.184.216.34"
            ),
            Arguments.of(
                "private ip with host in allowlist", true, List.of("wiremock"),
                "https://wiremock:8080/webhook", "10.0.0.5", "10.0.0.5"
            ),
            Arguments.of(
                "allowlisted host when https not required", false, List.of("wiremock"),
                "https://wiremock:8080/webhook", "10.0.0.5", "10.0.0.5"
            ),
            Arguments.of(
                "allowlisted host when https required", true, List.of("example.com"),
                "https://example.com/hook", "93.184.216.34", "93.184.216.34"
            ),
            Arguments.of(
                "ipv6 documentation address", true, List.of(),
                "https://example.com/hook", "2001:db8::1", "2001:db8"
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("restrictedAddressRows")
    void shouldThrowWithReasonWhenResolvedAddressIsRestricted(
        String rowName, String resolverIp, String urlString, String expectedMessage
    ) throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, List.of(), Duration.ofSeconds(30)
        );
        var resolver = createMockResolver(resolverIp);
        var validator = new WebhookUrlValidator(props, resolver);
        var url = new WebhookUrl(new URI(urlString));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(expectedMessage);
    }

    private static Stream<Arguments> restrictedAddressRows() {
        var restricted = "Webhook URL resolves to a restricted address";
        var uniqueLocal = "Webhook URL resolves to a unique local address";
        return Stream.of(
            Arguments.of("private 10-range address", "10.0.0.5", "https://private.example.com/hook", restricted),
            Arguments.of("private 192-range address", "192.168.1.1", "https://private.example.com/hook", restricted),
            Arguments.of("private 172-range address", "172.16.0.1", "https://private.example.com/hook", restricted),
            Arguments.of("loopback address", "127.0.0.1", "https://localhost.example.com/hook", restricted),
            Arguments.of("link-local metadata address", "169.254.169.254", "https://metadata.example.com/hook", restricted),
            Arguments.of("ipv6 loopback address", "::1", "https://example.com/hook", restricted),
            Arguments.of("ipv6 unique local address fd00", "fd00::1", "https://example.com/hook", uniqueLocal),
            Arguments.of("ipv6 unique local address fc00", "fc00::1", "https://example.com/hook", uniqueLocal),
            Arguments.of("ipv6 unique local address fd12", "fd12::1", "https://example.com/hook", uniqueLocal)
        );
    }

    @Test
    void shouldThrowWhenResolverThrowsDuringValidation() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, List.of(), Duration.ofSeconds(30)
        );
        Function<String, List<InetAddress>> failingResolver = host -> {
            throw new RuntimeException("DNS resolution failed");
        };
        var validator = new WebhookUrlValidator(props, failingResolver);
        var url = new WebhookUrl(new URI("https://example.com/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(Exception.class);
    }

    @Test
    void shouldThrowWhenHostDoesNotResolveUsingDefaultResolver() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, List.of(), Duration.ofSeconds(30)
        );
        var validator = new WebhookUrlValidator(props);
        var url = new WebhookUrl(new URI("https://does-not-exist.invalid/hook"));

        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Webhook host does not resolve: does-not-exist.invalid");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedSchemeRows")
    void shouldThrowWithReasonWhenSchemeIsRejected(
        String rowName, boolean requireHttps, List<String> allowlist, String uriString, String expectedMessage
    ) throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), requireHttps, allowlist, Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);
        var uri = URI.create(uriString);

        assertThatThrownBy(() -> validator.validate(uri))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(expectedMessage);
    }

    private static Stream<Arguments> rejectedSchemeRows() {
        return Stream.of(
            Arguments.of(
                "https required but scheme is http", true, List.of(),
                "http://example.com/hook", "HTTPS is required for webhook URLs"
            ),
            Arguments.of(
                "http scheme not in allowlist", false, List.of(),
                "http://example.com/hook", "HTTP scheme requires host to be in allowlist"
            )
        );
    }

    @Test
    void shouldThrowWhenUriHasNoHost() throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, List.of(), Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);
        var uriWithoutHost = URI.create("mailto:test@example.com");

        assertThatThrownBy(() -> validator.validate(uriWithoutHost))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Webhook URL must have a valid host");
    }

    @ParameterizedTest(name = "host=<{0}>")
    @NullAndEmptySource
    void shouldThrowWhenHostIsBlankInValidateHost(String host) throws Exception {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, List.of(), Duration.ofSeconds(30)
        );
        var resolver = createMockResolver("93.184.216.34");
        var validator = new WebhookUrlValidator(props, resolver);

        assertThatThrownBy(() -> validator.validateHost(host))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Host must not be null or empty");
    }

    @Test
    void shouldThrowWhenResolverReturnsNoAddressesInValidateHost() {
        var props = new WebhookProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), true, List.of(), Duration.ofSeconds(30)
        );
        Function<String, List<InetAddress>> emptyResolver = host -> List.of();
        var validator = new WebhookUrlValidator(props, emptyResolver);

        assertThatThrownBy(() -> validator.validateHost("example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unable to resolve host: example.com");
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
