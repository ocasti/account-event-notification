package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.infrastructure.fixtures.Entities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptJpaRepositoryIT extends PersistenceTestSupport {

    private static final EventId EVENT_ID = new EventId("test-event-1");

    /**
     * {@code notification_events.subscription_id} has a foreign key on {@code subscriptions};
     * this is one of the three rows Flyway seeds (V2__initial_subscriptions.sql), used here only
     * to satisfy that constraint — this test does not assert on subscription identity.
     */
    private static final String PERSISTED_SUBSCRIPTION_ID = "sub_client001";

    @Autowired
    private DeliveryAttemptJpaRepository deliveryAttemptRepository;

    @Autowired
    private NotificationEventJpaRepository notificationEventRepository;

    @Test
    void shouldCountOnlyUnexecutedUnclaimedAttemptsWhenCountingDueAttempts() {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(EVENT_ID)
            .withClientId(new ClientId("test-client"))
            .withEventKey(new EventKey("test.key"))
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();
        notificationEventRepository.save(Entities.notificationEvent(event));
        var past = hoursAgo(1);
        var future = hoursFromNow(1);

        var dueAttempt = DeliveryAttempts.anAttempt().withEventId(EVENT_ID).withNextAttemptAt(past).build();
        deliveryAttemptRepository.save(Entities.deliveryAttempt(dueAttempt));

        var claimedAttempt = DeliveryAttempts.anAttempt()
            .withEventId(EVENT_ID)
            .withNextAttemptAt(past)
            .withClaimedAt(Clocks.NOW)
            .withClaimedBy("worker-1")
            .build();
        deliveryAttemptRepository.save(Entities.deliveryAttempt(claimedAttempt));

        var futureAttempt = DeliveryAttempts.anAttempt().withEventId(EVENT_ID).withNextAttemptAt(future).build();
        deliveryAttemptRepository.save(Entities.deliveryAttempt(futureAttempt));

        var executedAttempt = DeliveryAttempts.anAttempt()
            .withEventId(EVENT_ID)
            .withNextAttemptAt(past)
            .withExecutedAt(Clocks.NOW)
            .withResponseStatus(200)
            .withLatency(Duration.ofMillis(100))
            .build();
        deliveryAttemptRepository.save(Entities.deliveryAttempt(executedAttempt));

        long result = deliveryAttemptRepository.countDue();

        assertThat(result).isEqualTo(1L);
    }

    /**
     * {@code countDue()} filters on Postgres's own {@code now()}, so "due" must be expressed
     * relative to the real wall clock rather than the fixed {@link Clocks#NOW}.
     */
    private Instant hoursAgo(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    private Instant hoursFromNow(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }
}
