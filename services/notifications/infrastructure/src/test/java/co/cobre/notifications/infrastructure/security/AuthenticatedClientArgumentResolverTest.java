package co.cobre.notifications.infrastructure.security;

import co.cobre.notifications.domain.ClientId;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.NativeWebRequest;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedClientArgumentResolverTest {

    @Test
    void supportsParameter_withClientIdAndAnnotation_returnsTrue() throws NoSuchMethodException {
        var resolver = new AuthenticatedClientArgumentResolver(mock(ClientIdResolver.class));

        var method = TestController.class.getMethod("testMethod", ClientId.class);
        var parameter = new MethodParameter(method, 0);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void supportsParameter_withoutAnnotation_returnsFalse() throws NoSuchMethodException {
        var resolver = new AuthenticatedClientArgumentResolver(mock(ClientIdResolver.class));

        var method = TestController.class.getMethod("testMethodWithout", ClientId.class);
        var parameter = new MethodParameter(method, 0);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    void resolveArgument_withValidJwt_returnsClientId() throws NoSuchMethodException {
        var clientIdResolver = mock(ClientIdResolver.class);
        var resolver = new AuthenticatedClientArgumentResolver(clientIdResolver);

        var jwt = createJwt("client-123");
        var token = new JwtAuthenticationToken(jwt, null);

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);

        try {
            var method = TestController.class.getMethod("testMethod", ClientId.class);
            var parameter = new MethodParameter(method, 0);
            var webRequest = mock(NativeWebRequest.class);
            var expectedClientId = new ClientId("client-123");

            when(clientIdResolver.resolve(jwt)).thenReturn(expectedClientId);

            var result = resolver.resolveArgument(parameter, null, webRequest, null);

            assertThat(result).isEqualTo(expectedClientId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Jwt createJwt(String clientId) {
        return new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", clientId)
        );
    }

    static class TestController {
        public void testMethod(@AuthenticatedClient ClientId clientId) {
        }

        public void testMethodWithout(ClientId clientId) {
        }
    }
}
