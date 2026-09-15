package co.cobre.notifications.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Properties for notification delivery worker.
 */
@ConfigurationProperties(prefix = "notifications.worker")
public record WorkerProperties(
    String workerId,
    int batchSize,
    int maxPerClient,
    Duration lease
) {
}
