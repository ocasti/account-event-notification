package co.cobre.notifications.infrastructure.worker;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMetricsTest {

    private SimpleMeterRegistry registry;
    private DeliveryMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DeliveryMetrics(registry);
    }

    /**
     * One row per counter: the calls made on the metrics, the meter that must
     * end up in the registry (name and tags) and the count expected on it.
     */
    static Stream<Arguments> counterRecordings() {
        return Stream.of(
            Arguments.of("registered twice for one client and once for another",
                (Consumer<DeliveryMetrics>) m -> {
                    m.registered("CLIENT123", "account.updated");
                    m.registered("CLIENT123", "account.updated");
                    m.registered("CLIENT456", "account.created");
                },
                "notifications.registered", Tags.of("client_id", "CLIENT123", "event_key", "account.updated"), 2.0),
            Arguments.of("skipped twice for the same client and event key",
                (Consumer<DeliveryMetrics>) m -> {
                    m.skipped("CLIENT789", "account.deleted");
                    m.skipped("CLIENT789", "account.deleted");
                },
                "notifications.skipped", Tags.of("client_id", "CLIENT789", "event_key", "account.deleted"), 2.0),
            Arguments.of("duplicate twice for one client and once for another",
                (Consumer<DeliveryMetrics>) m -> {
                    m.duplicate("CLIENT111", "account.updated");
                    m.duplicate("CLIENT111", "account.updated");
                    m.duplicate("CLIENT222", "order.placed");
                },
                "notifications.duplicates", Tags.of("client_id", "CLIENT111", "event_key", "account.updated"), 2.0),
            Arguments.of("delivered with two successes and one failure counts successes",
                (Consumer<DeliveryMetrics>) DeliveryMetricsTest::twoSuccessesAndOneFailure,
                "notifications.deliveries",
                Tags.of("client_id", "CLIENT111", "status", "success", "event_key", "order.placed"), 2.0),
            Arguments.of("delivered with two successes and one failure counts the failure",
                (Consumer<DeliveryMetrics>) DeliveryMetricsTest::twoSuccessesAndOneFailure,
                "notifications.deliveries",
                Tags.of("client_id", "CLIENT111", "status", "failure", "event_key", "order.placed"), 1.0),
            Arguments.of("lease expired three times",
                (Consumer<DeliveryMetrics>) m -> {
                    m.leaseExpired();
                    m.leaseExpired();
                    m.leaseExpired();
                },
                "notifications.leases.expired", Tags.empty(), 3.0),
            Arguments.of("batches of 15 and 20 add up their sizes",
                (Consumer<DeliveryMetrics>) m -> {
                    m.batchProcessed(15);
                    m.batchProcessed(20);
                },
                "notifications.batches", Tags.empty(), 35.0),
            Arguments.of("scheduler error twice",
                (Consumer<DeliveryMetrics>) m -> {
                    m.schedulerError();
                    m.schedulerError();
                },
                "notifications.scheduler.errors", Tags.empty(), 2.0)
        );
    }

    @ParameterizedTest(name = "{0} -> {2}{3} = {4}")
    @MethodSource("counterRecordings")
    void shouldAccumulateCounterWhenMetricIsRecorded(
        String scenario, Consumer<DeliveryMetrics> recording, String meterName, Tags tags, double expectedCount
    ) {
        recording.accept(metrics);

        var counter = registry.find(meterName).tags(tags).counter();

        assertThat(counter).as(scenario).isNotNull();
        assertThat(counter.count()).as(scenario).isEqualTo(expectedCount);
    }

    @Test
    void shouldCountEveryRecordingWhenWebhookLatencyIsTimed() {
        metrics.webhookLatency("CLIENT222", Duration.ofMillis(250));
        metrics.webhookLatency("CLIENT222", Duration.ofMillis(350));

        var timer = registry.find("notifications.webhook.latency").tag("client_id", "CLIENT222").timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    void shouldExposeLatestValueWhenAttemptsDueIsUpdatedRepeatedly() {
        metrics.attemptsDue(10);
        metrics.attemptsDue(25);
        metrics.attemptsDue(8);

        var gauge = registry.find("notifications.attempts.due").gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(8.0);
    }

    private static void twoSuccessesAndOneFailure(DeliveryMetrics m) {
        m.delivered("CLIENT111", "success", "order.placed");
        m.delivered("CLIENT111", "success", "order.placed");
        m.delivered("CLIENT111", "failure", "order.placed");
    }
}
