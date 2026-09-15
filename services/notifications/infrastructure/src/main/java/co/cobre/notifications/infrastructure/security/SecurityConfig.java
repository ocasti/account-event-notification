package co.cobre.notifications.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for JWT-based resource server.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for API security.
     * No session creation, CSRF disabled, /notification_events/** requires authentication,
     * health and prometheus endpoints are public, resource server with JWT.
     */
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtProperties props) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Configures the JWT decoder with public key and audience validation.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties props) {
        throw new UnsupportedOperationException("not implemented");
    }
}
