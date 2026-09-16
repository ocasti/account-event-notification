package co.cobre.notifications.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;


@ConfigurationProperties(prefix = "notifications.jwt")
public record JwtProperties(
    Resource publicKey,
    String audience,
    String clientClaim
) {}
