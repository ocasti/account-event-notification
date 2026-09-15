package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.infrastructure.security.AuthenticatedClientArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthenticatedClientArgumentResolver authenticatedClientArgumentResolver;

    public WebMvcConfig(AuthenticatedClientArgumentResolver authenticatedClientArgumentResolver) {
        this.authenticatedClientArgumentResolver = authenticatedClientArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedClientArgumentResolver);
    }
}
