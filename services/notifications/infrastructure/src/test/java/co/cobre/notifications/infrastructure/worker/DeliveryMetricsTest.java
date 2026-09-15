package co.cobre.notifications.infrastructure.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryMetricsTest {

    private SimpleMeterRegistry registry;
    private DeliveryMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DeliveryMetrics(registry);
    }

    @Test
    void registeredIncrementsCounter() {
        metrics.registered("CLIENT123", "account.updated");
        metrics.registered("CLIENT123", "account.updated");
        metrics.registered("CLIENT456", "account.created");

        Counter counter = registry.find("notifications.registered")
            .tag("client_id", "CLIENT123")
            .tag("event_key", "account.updated")
            .counter();

        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    void skippedIncrementsCounter() {
        metrics.skipped("CLIENT789", "account.deleted");
        metrics.skipped("CLIENT789", "account.deleted");

        Counter counter = registry.find("notifications.skipped")
            .tag("client_id", "CLIENT789")
            .tag("event_key", "account.deleted")
            .counter();

        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    void deliveredIncrementsCounter() {
        metrics.delivered("CLIENT111", "success", "order.placed");
        metrics.delivered("CLIENT111", "success", "order.placed");
        metrics.delivered("CLIENT111", "failure", "order.placed");

        Counter successCounter = registry.find("notifications.deliveries")
            .tag("client_id", "CLIENT111")
            .tag("status", "success")
            .tag("event_key", "order.placed")
            .counter();

        assertNotNull(successCounter);
        assertEquals(2.0, successCounter.count());

        Counter failureCounter = registry.find("notifications.deliveries")
            .tag("client_id", "CLIENT111")
            .tag("status", "failure")
            .tag("event_key", "order.placed")
            .counter();

        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());
    }

    @Test
    void webhookLatencyRecordsTimer() {
        metrics.webhookLatency("CLIENT222", Duration.ofMillis(250));
        metrics.webhookLatency("CLIENT222", Duration.ofMillis(350));

        Timer timer = registry.find("notifications.webhook.latency")
            .tag("client_id", "CLIENT222")
            .timer();

        assertNotNull(timer);
        assertEquals(2, timer.count());
    }

    @Test
    void leaseExpiredIncrementsCounter() {
        metrics.leaseExpired();
        metrics.leaseExpired();
        metrics.leaseExpired();

        Counter counter = registry.find("notifications.leases.expired").counter();

        assertNotNull(counter);
        assertEquals(3.0, counter.count());
    }

    @Test
    void attemptsDueTracksLatestGaugeValue() {
        metrics.attemptsDue(10);
        metrics.attemptsDue(25);
        metrics.attemptsDue(8);

        Gauge gauge = registry.find("notifications.attempts.due").gauge();

        assertNotNull(gauge);
        assertEquals(8.0, gauge.value());
    }

    @Test
    void batchProcessedIncrementsCounter() {
        metrics.batchProcessed(15);
        metrics.batchProcessed(20);

        Counter counter = registry.find("notifications.batches").counter();

        assertNotNull(counter);
        assertEquals(35.0, counter.count());
    }

    @Test
    void schedulerErrorIncrementsCounter() {
        metrics.schedulerError();
        metrics.schedulerError();

        Counter counter = registry.find("notifications.scheduler.errors").counter();

        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }
}
