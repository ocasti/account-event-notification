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
    void audienceMatches_whenClaimIsCollectionContainingExpectedAudience_returnsTrue() {
        assertThat(SecurityConfig.audienceMatches(List.of("account-event-notification"), "account-event-notification"))
            .isTrue();
    }

    @Test
    void audienceMatches_whenClaimIsCollectionMissingExpectedAudience_returnsFalse() {
        assertThat(SecurityConfig.audienceMatches(List.of("some-other-audience"), "account-event-notification"))
            .isFalse();
    }

    @Test
    void audienceMatches_whenClaimIsSingleStringEqualToExpected_returnsTrue() {
        assertThat(SecurityConfig.audienceMatches("account-event-notification", "account-event-notification"))
            .isTrue();
    }

    @Test
    void audienceMatches_whenClaimIsSingleStringDifferentFromExpected_returnsFalse() {
        assertThat(SecurityConfig.audienceMatches("some-other-audience", "account-event-notification"))
            .isFalse();
    }

    @Test
    void audienceMatches_whenClaimIsNull_returnsFalse() {
        assertThat(SecurityConfig.audienceMatches(null, "account-event-notification")).isFalse();
    }

    @Test
    void jwtDecoder_whenPublicKeyContentIsNotAValidKey_throwsIllegalStateException() {
        var invalidPem = "-----BEGIN PUBLIC KEY-----\nbm90YXZhbGlkS2V5\n-----END PUBLIC KEY-----\n";
        var resource = new ByteArrayResource(invalidPem.getBytes(StandardCharsets.UTF_8));
        var props = new JwtProperties(resource, "account-event-notification", "client_id");

        assertThatThrownBy(() -> config.jwtDecoder(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot load the JWT public key");
    }
}
