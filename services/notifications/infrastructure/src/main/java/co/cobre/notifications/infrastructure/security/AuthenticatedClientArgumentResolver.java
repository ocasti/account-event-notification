package co.cobre.notifications.infrastructure.security;

import co.cobre.notifications.domain.ClientId;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthenticatedClientArgumentResolver implements HandlerMethodArgumentResolver {
    private final ClientIdResolver clientIdResolver;

    public AuthenticatedClientArgumentResolver(ClientIdResolver clientIdResolver) {
        this.clientIdResolver = clientIdResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedClient.class) &&
            parameter.getParameterType().isAssignableFrom(ClientId.class);
    }

    @Override
    @Nullable
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Jwt jwt = getJwt();
        return clientIdResolver.resolve(jwt);
    }

    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("JWT not found in security context");
    }
}
