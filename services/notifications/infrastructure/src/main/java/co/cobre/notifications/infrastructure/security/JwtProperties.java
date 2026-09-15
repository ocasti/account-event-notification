package co.cobre.notifications.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * JWT configuration properties.
 */
@ConfigurationProperties(prefix = "notifications.jwt")
public record JwtProperties(
    Resource publicKey,
    String audience,
    String clientClaim
) {}
