package co.cobre.notifications.infrastructure.security;

import co.cobre.notifications.domain.model.ClientId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientIdResolverTest {

    @Test
    void resolve_extractsClientIdFromConfiguredClaim() {
        JwtProperties props = new JwtProperties(null, "account-event-notification", "sub");
        ClientIdResolver resolver = new ClientIdResolver(props);

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "CLIENT002");
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "RS256"), claims);

        ClientId result = resolver.resolve(jwt);

        assertEquals("CLIENT002", result.value());
    }

    @Test
    void resolve_throwsWhenClaimAbsent() {
        JwtProperties props = new JwtProperties(null, "account-event-notification", "sub");
        ClientIdResolver resolver = new ClientIdResolver(props);

        Map<String, Object> claims = new HashMap<>();
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "RS256"), claims);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(jwt));
    }
}
