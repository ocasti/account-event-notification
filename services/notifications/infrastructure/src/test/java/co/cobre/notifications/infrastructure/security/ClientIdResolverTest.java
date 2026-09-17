package co.cobre.notifications.infrastructure.security;

import co.cobre.notifications.domain.ClientId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIdResolverTest {

    @Test
    void shouldExtractClientIdWhenClaimIsConfigured() {
        JwtProperties props = new JwtProperties(null, "account-event-notification", "sub");
        ClientIdResolver resolver = new ClientIdResolver(props);
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "CLIENT002");
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "RS256"), claims);

        ClientId result = resolver.resolve(jwt);

        assertThat(result.value()).isEqualTo("CLIENT002");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenClaimIsAbsent() {
        JwtProperties props = new JwtProperties(null, "account-event-notification", "sub");
        ClientIdResolver resolver = new ClientIdResolver(props);
        Map<String, Object> claims = new HashMap<>();
        claims.put("other", "value");
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "RS256"), claims);

        assertThatThrownBy(() -> resolver.resolve(jwt))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
