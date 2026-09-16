package co.cobre.notifications.infrastructure.security;

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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                aud -> audienceMatches(aud, props.audience())
            );

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                audienceValidator
            ));

            return decoder;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot load the JWT public key", e);
        }
    }

    /**
     * Matches the JWT {@code aud} claim against the configured audience. The claim
     * may come over the wire either as a JSON array (parsed as a {@link Collection})
     * or, when there is a single audience, as a bare string per RFC 7519 4.1.3.
     */
    static boolean audienceMatches(Object aud, String expectedAudience) {
        if (aud instanceof Collection) {
            return ((Collection<?>) aud).stream()
                .map(Object::toString)
                .anyMatch(a -> a.equals(expectedAudience));
        }
        return aud != null && aud.toString().equals(expectedAudience);
    }
}
