package co.cobre.notifications.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notifications.retry")
public record RetryProperties(
    Duration baseDelay,
    double factor,
    Duration maxDelay,
    double jitterRatio,
    int maxAttempts
) {
}
