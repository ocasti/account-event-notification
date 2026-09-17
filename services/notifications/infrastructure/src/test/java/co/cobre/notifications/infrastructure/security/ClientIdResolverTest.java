package co.cobre.notifications.infrastructure.security;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.fixtures.Clocks;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIdResolverTest {

    @Test
    void shouldExtractClientIdWhenClaimIsConfigured() {
        var props = new JwtProperties(null, "account-event-notification", "sub");
        var resolver = new ClientIdResolver(props);
        var claims = new HashMap<String, Object>();
        claims.put("sub", "CLIENT002");
        var jwt = new Jwt("token", Clocks.NOW, Clocks.NOW.plusSeconds(3600), Map.of("alg", "RS256"), claims);

        ClientId result = resolver.resolve(jwt);

        assertThat(result.value()).isEqualTo("CLIENT002");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenClaimIsAbsent() {
        var props = new JwtProperties(null, "account-event-notification", "sub");
        var resolver = new ClientIdResolver(props);
        var claims = new HashMap<String, Object>();
        claims.put("other", "value");
        var jwt = new Jwt("token", Clocks.NOW, Clocks.NOW.plusSeconds(3600), Map.of("alg", "RS256"), claims);

        assertThatThrownBy(() -> resolver.resolve(jwt))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
