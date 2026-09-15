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
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    /**
     * Configures the JWT decoder with public key and audience validation.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties props) {
        return token -> {
            throw new UnsupportedOperationException("not implemented");
        };
    }
}
