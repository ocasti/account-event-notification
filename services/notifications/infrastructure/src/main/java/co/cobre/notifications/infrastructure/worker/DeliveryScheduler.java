package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import co.cobre.notifications.infrastructure.worker.DeliveryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for processing due deliveries.
 */
@Component
@Profile("worker")
public class DeliveryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryScheduler.class);

    private final ProcessDueDeliveries processDueDeliveries;
    private final DeliveryMetrics metrics;

    
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
        try {
            int processed = processDueDeliveries.processBatch();
            metrics.batchProcessed(processed);
        } catch (Exception e) {
            logger.error("Scheduler error processing batch", e);
            metrics.schedulerError();
        }
    }
}
