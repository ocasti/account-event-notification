package co.cobre.notifications.infrastructure.scheduler;

import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import co.cobre.notifications.infrastructure.metrics.DeliveryMetrics;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for processing due deliveries.
 */
@Component
@Profile("worker")
public class DeliveryScheduler {

    private final ProcessDueDeliveries processDueDeliveries;
    private final DeliveryMetrics metrics;

    /**
     * Creates a new delivery scheduler.
     */
    public DeliveryScheduler(
        ProcessDueDeliveries processDueDeliveries,
        DeliveryMetrics metrics
    ) {
        this.processDueDeliveries = processDueDeliveries;
        this.metrics = metrics;
    }

    /**
     * Polls for due deliveries and processes them.
     */
    @Scheduled(fixedDelayString = "${notifications.worker.poll-interval:1s}")
    public void tick() {
        throw new UnsupportedOperationException("not implemented");
    }
}
