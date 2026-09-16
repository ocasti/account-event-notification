package co.cobre.notifications.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics for notification delivery operations.
 */
@Component
public class DeliveryMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger attemptsDueValue;

    
    public DeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.attemptsDueValue = new AtomicInteger(0);
        io.micrometer.core.instrument.Gauge.builder("notifications.attempts.due", attemptsDueValue, AtomicInteger::get)
            .register(registry);
    }

    /**
     * Records a registered notification.
     */
    public void registered(String clientId, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.registered")
            .tag("client_id", clientId)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }

    /**
     * Records a skipped notification.
     */
    public void skipped(String clientId, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.skipped")
            .tag("client_id", clientId)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }

    /**
     * Records a delivered notification.
     */
    public void delivered(String clientId, String status, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.deliveries")
            .tag("client_id", clientId)
            .tag("status", status)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }

    /**
     * Records webhook latency.
     */
    public void webhookLatency(String clientId, Duration latency) {
        io.micrometer.core.instrument.Timer.builder("notifications.webhook.latency")
            .tag("client_id", clientId)
            .register(registry)
            .record(latency);
    }

    /**
     * Records a lease expiration.
     */
    public void leaseExpired() {
        io.micrometer.core.instrument.Counter.builder("notifications.leases.expired")
            .register(registry)
            .increment();
    }

    /**
     * Records the count of due attempts.
     */
    public void attemptsDue(int count) {
        attemptsDueValue.set(count);
    }

    /**
     * Records a batch processing result.
     */
    public void batchProcessed(int count) {
        io.micrometer.core.instrument.Counter.builder("notifications.batches")
            .register(registry)
            .increment(count);
    }

    /**
     * Records a scheduler error.
     */
    public void schedulerError() {
        io.micrometer.core.instrument.Counter.builder("notifications.scheduler.errors")
            .register(registry)
            .increment();
    }

    /**
     * Records a message whose event id was already registered; the queue redelivered it.
     */
    public void duplicate(String clientId, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.duplicates")
            .tag("client_id", clientId)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }
}
