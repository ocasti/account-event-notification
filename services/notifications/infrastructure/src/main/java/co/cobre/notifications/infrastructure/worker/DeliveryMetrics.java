package co.cobre.notifications.infrastructure.worker;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

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

    public void registered(String clientId, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.registered")
            .tag("client_id", clientId)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }

    public void skipped(String clientId, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.skipped")
            .tag("client_id", clientId)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }

    public void delivered(String clientId, String status, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.deliveries")
            .tag("client_id", clientId)
            .tag("status", status)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }

    public void webhookLatency(String clientId, Duration latency) {
        io.micrometer.core.instrument.Timer.builder("notifications.webhook.latency")
            .tag("client_id", clientId)
            .register(registry)
            .record(latency);
    }

    public void leaseExpired() {
        io.micrometer.core.instrument.Counter.builder("notifications.leases.expired")
            .register(registry)
            .increment();
    }

    public void attemptsDue(int count) {
        attemptsDueValue.set(count);
    }

    public void batchProcessed(int count) {
        io.micrometer.core.instrument.Counter.builder("notifications.batches")
            .register(registry)
            .increment(count);
    }

    public void schedulerError() {
        io.micrometer.core.instrument.Counter.builder("notifications.scheduler.errors")
            .register(registry)
            .increment();
    }

    public void duplicate(String clientId, String eventKey) {
        io.micrometer.core.instrument.Counter.builder("notifications.duplicates")
            .tag("client_id", clientId)
            .tag("event_key", eventKey)
            .register(registry)
            .increment();
    }
}
