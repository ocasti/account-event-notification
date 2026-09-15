package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.application.usecase.DeliveryWorkerSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for the delivery worker profile.
 */
@Configuration
@Profile("worker")
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {

    /**
     * Creates delivery worker settings from properties.
     */
    @Bean
    DeliveryWorkerSettings deliveryWorkerSettings(WorkerProperties properties) {
        return new DeliveryWorkerSettings(
            properties.workerId(),
            properties.batchSize(),
            properties.maxPerClient(),
            properties.lease()
        );
    }
}
