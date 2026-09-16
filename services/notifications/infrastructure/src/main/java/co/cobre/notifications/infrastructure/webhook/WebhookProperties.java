package co.cobre.notifications.infrastructure.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;


@ConfigurationProperties(prefix = "notifications.webhook")
public record WebhookProperties(
    Duration connectTimeout,
    Duration readTimeout,
    boolean requireHttps,
    List<String> allowlist,
    Duration timestampTolerance
) {}
