package co.cobre.notifications.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void shouldMatchAudienceWhenClaimCollectionContainsExpectedAudience() {
        var result = SecurityConfig.audienceMatches(List.of("account-event-notification"), "account-event-notification");

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotMatchAudienceWhenClaimCollectionMissesExpectedAudience() {
        var result = SecurityConfig.audienceMatches(List.of("some-other-audience"), "account-event-notification");

        assertThat(result).isFalse();
    }

    @Test
    void shouldMatchAudienceWhenClaimStringEqualsExpectedAudience() {
        var result = SecurityConfig.audienceMatches("account-event-notification", "account-event-notification");

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotMatchAudienceWhenClaimStringDiffersFromExpectedAudience() {
        var result = SecurityConfig.audienceMatches("some-other-audience", "account-event-notification");

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotMatchAudienceWhenClaimIsNull() {
        var result = SecurityConfig.audienceMatches(null, "account-event-notification");

        assertThat(result).isFalse();
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenPublicKeyContentIsInvalid() {
        var invalidPem = "-----BEGIN PUBLIC KEY-----\nbm90YXZhbGlkS2V5\n-----END PUBLIC KEY-----\n";
        var resource = new ByteArrayResource(invalidPem.getBytes(StandardCharsets.UTF_8));
        var props = new JwtProperties(resource, "account-event-notification", "client_id");

        assertThatThrownBy(() -> config.jwtDecoder(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot load the JWT public key");
    }
}
