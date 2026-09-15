package co.cobre.notifications.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for scheduled tasks (worker profile only).
 * Enables scheduling for the delivery scheduler and SQS listener components.
 */
@Configuration
@Profile("worker")
@EnableScheduling
public class SchedulingConfig {
}
