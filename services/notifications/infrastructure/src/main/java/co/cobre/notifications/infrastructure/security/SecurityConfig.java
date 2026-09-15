package co.cobre.notifications.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

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
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtProperties props) {
        try {
            var publicKeyContent = new String(props.publicKey().getContentAsByteArray())
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

            byte[] decodedKey = Base64.getDecoder().decode(publicKeyContent);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(spec);

            OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
                JwtClaimNames.AUD,
                aud -> {
                    if (aud instanceof Collection) {
                        return ((Collection<?>) aud).stream()
                            .map(Object::toString)
                            .anyMatch(a -> a.equals(props.audience()));
                    }
                    return aud != null && aud.toString().equals(props.audience());
                }
            );

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                audienceValidator
            ));

            return decoder;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT decoder", e);
        }
    }
}
