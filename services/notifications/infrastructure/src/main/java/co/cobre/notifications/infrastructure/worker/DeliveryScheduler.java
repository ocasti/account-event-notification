package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

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
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // keeps the scheduler alive across any single batch failure
    public void tick() {
        try {
            int processed = processDueDeliveries.processBatch();
            metrics.batchProcessed(processed);
        } catch (Exception e) {
            logger.error("Scheduler error processing batch: {}", rootCause(e).getMessage(), e);
            metrics.schedulerError();
        }
    }

    private static Throwable rootCause(Exception e) {
        if (!(e instanceof CompletionException) || e.getCause() == null) {
            return e;
        }
        return e.getCause();
    }
}
