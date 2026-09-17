package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryResult;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.infrastructure.fixtures.Entities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DeliveryAttemptRepositoryAdapterIT extends PersistenceTestSupport {

    /**
     * {@code notification_events.subscription_id} has a foreign key on {@code subscriptions};
     * this is one of the three rows Flyway seeds (V2__initial_subscriptions.sql), used here only
     * to satisfy that constraint — none of these tests assert on subscription identity.
     */
    private static final String PERSISTED_SUBSCRIPTION_ID = "sub_client001";

    @Autowired
    private DeliveryAttemptRepositoryAdapter adapter;

    @Autowired
    private DeliveryAttemptJpaRepository jpaRepository;

    @Autowired
    private NotificationEventJpaRepository eventJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void cleanupTestData() {
        jdbcTemplate.execute("DELETE FROM delivery_attempts");
        jdbcTemplate.execute("DELETE FROM notification_events");
    }

    @Test
    @Transactional
    void shouldReturnSavedAttemptWhenFindingByEvent() {
        var eventId = new EventId("evt-attempt-001");
        createEvent(eventId);
        var attempt = DeliveryAttempt.first(eventId, 0, Clocks.NOW, AttemptOrigin.SYSTEM);

        adapter.save(attempt);
        var found = adapter.findByEvent(eventId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).id()).isEqualTo(attempt.id());
    }

    @Test
    @Transactional
    void shouldOrderAttemptsByCycleThenAttemptNumberWhenFindingByEvent() {
        var eventId = new EventId("evt-order-001");
        createEvent(eventId);
        var secondAttemptInCycleZero = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withCycle(0).withAttemptNumber(2).withNextAttemptAt(Clocks.NOW).build();
        var firstAttemptInCycleOne = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withCycle(1).withAttemptNumber(1).withNextAttemptAt(Clocks.NOW).build();
        var firstAttemptInCycleZero = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withCycle(0).withAttemptNumber(1).withNextAttemptAt(Clocks.NOW).build();

        adapter.save(secondAttemptInCycleZero);
        adapter.save(firstAttemptInCycleOne);
        adapter.save(firstAttemptInCycleZero);
        var found = adapter.findByEvent(eventId);

        assertThat(found).extracting(DeliveryAttempt::cycle, DeliveryAttempt::attemptNumber)
            .containsExactly(tuple(0, 1), tuple(0, 2), tuple(1, 1));
    }

    @Test
    @Transactional
    void shouldExcludeExecutedAttemptsWhenClaimingDueAttempts() {
        var eventId = new EventId("evt-executed-001");
        createEvent(eventId);
        var executedAttempt = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withNextAttemptAt(secondsAgo(10)).withExecutedAt(dbNow()).build();
        adapter.save(executedAttempt);
        var claim = new DeliveryClaim(dbNow(), 10, 10, Ids.WORKER_1, Duration.ofSeconds(16));

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).isEmpty();
    }

    @Test
    @Transactional
    void shouldClaimOnlyUnclaimedDueAttemptWhenMultipleAttemptsExist() {
        var dueEventId = new EventId("evt-claim-001");
        var executedEventId = new EventId("evt-claim-002");
        createEvent(dueEventId);
        createEvent(executedEventId);
        var pastDue = secondsAgo(10);
        var dueAttempt = DeliveryAttempts.anAttempt()
            .withEventId(dueEventId).withAttemptNumber(1).withNextAttemptAt(pastDue).build();
        var futureAttempt = DeliveryAttempts.anAttempt()
            .withEventId(dueEventId).withAttemptNumber(2).withNextAttemptAt(secondsFromNow(100)).build();
        var executedAttempt = DeliveryAttempts.anAttempt()
            .withEventId(executedEventId).withAttemptNumber(1).withNextAttemptAt(pastDue).withExecutedAt(dbNow()).build();
        adapter.save(dueAttempt);
        adapter.save(futureAttempt);
        adapter.save(executedAttempt);
        var claim = new DeliveryClaim(dbNow(), 10, 10, Ids.WORKER_1, Duration.ofSeconds(16));

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(dueAttempt.id());
        assertThat(claimed.get(0).claimedAt()).isPresent();
        assertThat(claimed.get(0).claimedBy()).contains(Ids.WORKER_1);
    }

    @Test
    @Transactional
    void shouldClaimOnlyAttemptWithExpiredLeaseWhenClaimingDueAttempts() {
        var eventId = new EventId("evt-lease-001");
        createEvent(eventId);
        var pastDue = secondsAgo(30);
        var lease = Duration.ofSeconds(16);
        var attemptWithActiveLease = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withAttemptNumber(1).withNextAttemptAt(pastDue)
            .withClaimedAt(secondsAgo(15)).withClaimedBy("worker-a").build();
        var attemptWithExpiredLease = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withAttemptNumber(2).withNextAttemptAt(pastDue)
            .withClaimedAt(secondsAgo(20)).withClaimedBy("worker-b").build();
        adapter.save(attemptWithActiveLease);
        adapter.save(attemptWithExpiredLease);
        var claim = new DeliveryClaim(dbNow(), 10, 10, "worker-2", lease);

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(attemptWithExpiredLease.id());
    }

    @Test
    @Transactional
    void shouldCapClaimedAttemptsAtMaxPerClientWhenClientHasMoreDueAttempts() {
        var firstEventId = new EventId("evt-max-001");
        var secondEventId = new EventId("evt-max-002");
        createEvent(firstEventId);
        createEvent(secondEventId);
        var pastDue = secondsAgo(100);
        saveDueAttempts(firstEventId, 4, pastDue);
        saveDueAttempts(secondEventId, 4, pastDue);
        var claim = new DeliveryClaim(dbNow(), 20, 5, Ids.WORKER_1, Duration.ofSeconds(16));

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).hasSize(5);
    }

    @Test
    @Transactional
    void shouldRecordResultWhenWorkerMatchesClaimant() {
        var eventId = new EventId("evt-result-001");
        createEvent(eventId);
        var attempt = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withNextAttemptAt(Clocks.NOW)
            .withClaimedAt(Clocks.NOW.minusSeconds(5)).withClaimedBy(Ids.WORKER_1).build();
        adapter.save(attempt);
        var result = new DeliveryResult(Optional.of(200), Optional.empty(), Optional.of(Duration.ofMillis(150)));
        var executed = attempt.executed(Clocks.NOW.plusSeconds(1), Ids.WORKER_1, result);

        var success = adapter.recordResultIf(executed, Ids.WORKER_1);

        assertThat(success).isTrue();
        var found = adapter.findByEvent(eventId).get(0);
        assertThat(found.executedAt()).isPresent();
        assertThat(found.responseStatus()).contains(200);
        assertThat(found.latency()).contains(Duration.ofMillis(150));
    }

    @Test
    @Transactional
    void shouldNotRecordResultWhenWorkerDoesNotMatchClaimant() {
        var eventId = new EventId("evt-wrong-worker-001");
        createEvent(eventId);
        var attempt = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withNextAttemptAt(Clocks.NOW)
            .withClaimedAt(Clocks.NOW.minusSeconds(5)).withClaimedBy(Ids.WORKER_1).build();
        adapter.save(attempt);
        var result = new DeliveryResult(Optional.of(500), Optional.of("error"), Optional.of(Duration.ofMillis(100)));
        var executed = attempt.executed(Clocks.NOW.plusSeconds(1), "worker-2", result);

        var success = adapter.recordResultIf(executed, "worker-2");

        assertThat(success).isFalse();
        var found = adapter.findByEvent(eventId).get(0);
        assertThat(found.executedAt()).isEmpty();
    }

    @Test
    @Transactional
    void shouldNotRecordResultWhenAttemptAlreadyExecuted() {
        var eventId = new EventId("evt-executed-001");
        createEvent(eventId);
        var attempt = DeliveryAttempts.anAttempt()
            .withEventId(eventId).withNextAttemptAt(Clocks.NOW)
            .withClaimedAt(Clocks.NOW.minusSeconds(5)).withClaimedBy(Ids.WORKER_1)
            .withExecutedAt(Clocks.NOW).withResponseStatus(200).withLatency(Duration.ofMillis(50)).build();
        adapter.save(attempt);
        var result = new DeliveryResult(Optional.of(201), Optional.empty(), Optional.of(Duration.ofMillis(100)));
        var executed = attempt.executed(Clocks.NOW.plusSeconds(1), Ids.WORKER_1, result);

        var success = adapter.recordResultIf(executed, Ids.WORKER_1);

        assertThat(success).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldClaimEachDueAttemptExactlyOnceWhenWorkersClaimConcurrently() throws InterruptedException {
        var baseTime = dbNow();
        var pastDue = baseTime.minusSeconds(30);
        createEventsWithDueAttempts(20, pastDue);

        var result = claimAcrossWorkers(4, baseTime);

        assertThat(result.finishedInTime()).isTrue();
        assertThat(result.claimedIds()).hasSize(20);
    }

    @Test
    @Transactional
    void shouldReturnCountsOnlyForEventsHavingAttemptsWhenCountingByEvents() {
        var eventWithThreeAttempts = new EventId("evt-count-001");
        var eventWithOneAttempt = new EventId("evt-count-002");
        var eventWithNoAttempts = new EventId("evt-count-003");
        createEvent(eventWithThreeAttempts);
        createEvent(eventWithOneAttempt);
        createEvent(eventWithNoAttempts);
        var firstAttemptForBusyEvent = DeliveryAttempt.first(eventWithThreeAttempts, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        var secondAttemptForBusyEvent = DeliveryAttempt.first(eventWithThreeAttempts, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        var thirdAttemptForBusyEvent = DeliveryAttempt.first(eventWithThreeAttempts, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        var onlyAttemptForQuietEvent = DeliveryAttempt.first(eventWithOneAttempt, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        adapter.save(firstAttemptForBusyEvent);
        adapter.save(secondAttemptForBusyEvent);
        adapter.save(thirdAttemptForBusyEvent);
        adapter.save(onlyAttemptForQuietEvent);

        var counts = adapter.countByEvents(List.of(eventWithThreeAttempts, eventWithOneAttempt, eventWithNoAttempts));

        assertThat(counts).hasSize(2);
        assertThat(counts.get(eventWithThreeAttempts)).isEqualTo(3);
        assertThat(counts.get(eventWithOneAttempt)).isEqualTo(1);
        assertThat(counts).doesNotContainKey(eventWithNoAttempts);
    }

    @Test
    @Transactional
    void shouldReturnEmptyMapWhenCountingByEventsWithEmptyList() {
        var counts = adapter.countByEvents(List.of());

        assertThat(counts).isEmpty();
    }

    private void createEvent(EventId eventId) {
        eventJpaRepository.save(Entities.notificationEvent(
            NotificationEvents.aPendingEvent().withEventId(eventId).withSubscriptionId(PERSISTED_SUBSCRIPTION_ID).build()));
    }

    private void saveDueAttempts(EventId eventId, int count, Instant dueAt) {
        IntStream.range(0, count).forEach(i -> adapter.save(
            DeliveryAttempts.anAttempt().withEventId(eventId).withNextAttemptAt(dueAt).build()));
    }

    private void createEventsWithDueAttempts(int count, Instant dueAt) {
        IntStream.range(0, count).forEach(i -> {
            var eventId = new EventId("evt-concurrent-" + i);
            createEvent(eventId);
            adapter.save(DeliveryAttempts.anAttempt().withEventId(eventId).withNextAttemptAt(dueAt).build());
        });
    }

    private record ConcurrentClaimOutcome(Set<UUID> claimedIds, boolean finishedInTime) {
    }

    private ConcurrentClaimOutcome claimAcrossWorkers(int workerCount, Instant baseTime) throws InterruptedException {
        Set<UUID> allClaimedIds = ConcurrentHashMap.newKeySet();
        var latch = new CountDownLatch(workerCount);
        var executor = Executors.newFixedThreadPool(workerCount);

        IntStream.range(0, workerCount).forEach(i -> {
            var workerId = "worker-" + i;
            executor.submit(() -> {
                try {
                    var claim = new DeliveryClaim(baseTime, 20, Integer.MAX_VALUE, workerId, Duration.ofSeconds(16));
                    var claimed = adapter.claimDue(claim);
                    allClaimedIds.addAll(claimed.stream().map(DeliveryAttempt::id).toList());
                } finally {
                    latch.countDown();
                }
            });
        });

        var finishedInTime = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        return new ConcurrentClaimOutcome(allClaimedIds, finishedInTime);
    }

    /**
     * {@code claimDueAttempts} compares {@code next_attempt_at} and {@code claimed_at} against
     * Postgres' own {@code now()} (see {@link DeliveryAttemptJpaRepository#claimDueAttempts}),
     * not the application clock, so due/expired-lease fixtures in this class are built relative
     * to the real wall clock rather than {@link Clocks#NOW}.
     */
    private static Instant dbNow() {
        return Instant.now();
    }

    private static Instant secondsAgo(long seconds) {
        return Instant.now().minusSeconds(seconds);
    }

    private static Instant secondsFromNow(long seconds) {
        return Instant.now().plusSeconds(seconds);
    }
}
