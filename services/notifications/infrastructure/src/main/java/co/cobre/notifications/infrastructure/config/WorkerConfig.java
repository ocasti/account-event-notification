package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.application.usecase.DeliveryWorkerSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for delivery worker settings (profile-independent).
 * Provides the DeliveryWorkerSettings bean used by ProcessDueDeliveries.
 * Scheduling is configured in SchedulingConfig with @Profile("worker").
 */
@Configuration
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {

    
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
