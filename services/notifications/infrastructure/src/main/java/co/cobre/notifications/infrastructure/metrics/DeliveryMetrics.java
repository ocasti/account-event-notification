package co.cobre.notifications.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metrics for notification delivery operations.
 */
@Component
public class DeliveryMetrics {

    private final MeterRegistry registry;

    /**
     * Creates a new delivery metrics instance.
     */
    public DeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records a registered notification.
     */
    public void registered(String clientId, String eventKey) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Records a skipped notification.
     */
    public void skipped(String clientId, String eventKey) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Records a delivered notification.
     */
    public void delivered(String clientId, String status, String eventKey) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Records webhook latency.
     */
    public void webhookLatency(String clientId, Duration latency) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Records a lease expiration.
     */
    public void leaseExpired() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Records the count of due attempts.
     */
    public void attemptsDue(int count) {
        throw new UnsupportedOperationException("not implemented");
    }
}
