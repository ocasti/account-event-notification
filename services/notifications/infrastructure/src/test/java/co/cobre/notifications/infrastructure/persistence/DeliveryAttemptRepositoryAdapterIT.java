package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryResult;
import co.cobre.notifications.domain.EventId;
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
        var attempt = DeliveryAttempt.first(eventId, 0, Instant.now(), AttemptOrigin.SYSTEM);

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
        var att1 = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 2, Instant.now(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var att2 = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 1, 1, Instant.now(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var att3 = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, Instant.now(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(att1);
        adapter.save(att2);
        adapter.save(att3);
        var found = adapter.findByEvent(eventId);

        assertThat(found).extracting(DeliveryAttempt::cycle, DeliveryAttempt::attemptNumber)
            .containsExactly(tuple(0, 1), tuple(0, 2), tuple(1, 1));
    }

    @Test
    @Transactional
    void shouldExcludeExecutedAttemptsWhenClaimingDueAttempts() {
        var eventId = new EventId("evt-executed-001");
        createEvent(eventId);
        var now = Instant.now();
        var pastDue = now.minusSeconds(10);
        var executeAttempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, pastDue, Optional.empty(), Optional.empty(),
            Optional.of(now), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        adapter.save(executeAttempt);
        var claim = new DeliveryClaim(now, 10, 10, "worker-1", Duration.ofSeconds(16));

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).isEmpty();
    }

    @Test
    @Transactional
    void shouldClaimOnlyUnclaimedDueAttemptWhenMultipleAttemptsExist() {
        var eventId1 = new EventId("evt-claim-001");
        var eventId2 = new EventId("evt-claim-002");
        createEvent(eventId1);
        createEvent(eventId2);
        var now = Instant.now();
        var pastDue = now.minusSeconds(10);
        var future = now.plusSeconds(100);
        var dueAttempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId1, 0, 1, pastDue, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var futureAttempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId1, 0, 2, future, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var executeAttempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId2, 0, 1, pastDue, Optional.empty(), Optional.empty(),
            Optional.of(now), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        adapter.save(dueAttempt);
        adapter.save(futureAttempt);
        adapter.save(executeAttempt);
        var claim = new DeliveryClaim(now, 10, 10, "worker-1", Duration.ofSeconds(16));

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(dueAttempt.id());
        assertThat(claimed.get(0).claimedAt()).isPresent();
        assertThat(claimed.get(0).claimedBy()).contains("worker-1");
    }

    @Test
    @Transactional
    void shouldClaimOnlyAttemptWithExpiredLeaseWhenClaimingDueAttempts() {
        var eventId = new EventId("evt-lease-001");
        createEvent(eventId);
        var now = Instant.now();
        var pastDue = now.minusSeconds(30);
        var lease = Duration.ofSeconds(16);
        var att1 = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, pastDue, Optional.of(now.minusSeconds(15)),
            Optional.of("worker-a"), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), AttemptOrigin.SYSTEM
        );
        var att2 = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 2, pastDue, Optional.of(now.minusSeconds(20)),
            Optional.of("worker-b"), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), AttemptOrigin.SYSTEM
        );
        adapter.save(att1);
        adapter.save(att2);
        var claim = new DeliveryClaim(now, 10, 10, "worker-2", lease);

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(att2.id());
    }

    @Test
    @Transactional
    void shouldCapClaimedAttemptsAtMaxPerClientWhenClientHasMoreDueAttempts() {
        var eventId1 = new EventId("evt-max-001");
        var eventId2 = new EventId("evt-max-002");
        createEvent(eventId1);
        createEvent(eventId2);
        var now = Instant.now();
        var pastDue = now.minusSeconds(100);
        saveDueAttempts(eventId1, 4, pastDue);
        saveDueAttempts(eventId2, 4, pastDue);
        var claim = new DeliveryClaim(now, 20, 5, "worker-1", Duration.ofSeconds(16));

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).hasSize(5);
    }

    @Test
    @Transactional
    void shouldRecordResultWhenWorkerMatchesClaimant() {
        var eventId = new EventId("evt-result-001");
        createEvent(eventId);
        var now = Instant.now();
        var attempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, now,
            Optional.of(now.minusSeconds(5)), Optional.of("worker-1"),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        adapter.save(attempt);
        var result = new DeliveryResult(Optional.of(200), Optional.empty(), Optional.of(Duration.ofMillis(150)));
        var executed = attempt.executed(now.plusSeconds(1), "worker-1", result);

        var success = adapter.recordResultIf(executed, "worker-1");

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
        var now = Instant.now();
        var attempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, now,
            Optional.of(now.minusSeconds(5)), Optional.of("worker-1"),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        adapter.save(attempt);
        var result = new DeliveryResult(Optional.of(500), Optional.of("error"), Optional.of(Duration.ofMillis(100)));
        var executed = attempt.executed(now.plusSeconds(1), "worker-2", result);

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
        var now = Instant.now();
        var attempt = new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, now,
            Optional.of(now.minusSeconds(5)), Optional.of("worker-1"),
            Optional.of(now), Optional.of(200), Optional.empty(), Optional.of(Duration.ofMillis(50)), AttemptOrigin.SYSTEM
        );
        adapter.save(attempt);
        var result = new DeliveryResult(Optional.of(201), Optional.empty(), Optional.of(Duration.ofMillis(100)));
        var executed = attempt.executed(now.plusSeconds(1), "worker-1", result);

        var success = adapter.recordResultIf(executed, "worker-1");

        assertThat(success).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldClaimEachDueAttemptExactlyOnceWhenWorkersClaimConcurrently() throws InterruptedException {
        var baseTime = Instant.now();
        var pastDue = baseTime.minusSeconds(30);
        createEventsWithDueAttempts(20, pastDue);

        var result = claimAcrossWorkers(4, baseTime);

        assertThat(result.finishedInTime()).isTrue();
        assertThat(result.claimedIds()).hasSize(20);
    }

    @Test
    @Transactional
    void shouldReturnCountsOnlyForEventsHavingAttemptsWhenCountingByEvents() {
        var eventId1 = new EventId("evt-count-001");
        var eventId2 = new EventId("evt-count-002");
        var eventId3 = new EventId("evt-count-003");
        createEvent(eventId1);
        createEvent(eventId2);
        createEvent(eventId3);
        var attempt1 = DeliveryAttempt.first(eventId1, 0, Instant.now(), AttemptOrigin.SYSTEM);
        var attempt2 = DeliveryAttempt.first(eventId1, 0, Instant.now(), AttemptOrigin.SYSTEM);
        var attempt3 = DeliveryAttempt.first(eventId1, 0, Instant.now(), AttemptOrigin.SYSTEM);
        var attempt4 = DeliveryAttempt.first(eventId2, 0, Instant.now(), AttemptOrigin.SYSTEM);
        adapter.save(attempt1);
        adapter.save(attempt2);
        adapter.save(attempt3);
        adapter.save(attempt4);

        var counts = adapter.countByEvents(List.of(eventId1, eventId2, eventId3));

        assertThat(counts).hasSize(2);
        assertThat(counts.get(eventId1)).isEqualTo(3);
        assertThat(counts.get(eventId2)).isEqualTo(1);
        assertThat(counts).doesNotContainKey(eventId3);
    }

    @Test
    @Transactional
    void shouldReturnEmptyMapWhenCountingByEventsWithEmptyList() {
        var counts = adapter.countByEvents(List.of());

        assertThat(counts).isEmpty();
    }

    private void createEvent(EventId eventId) {
        var entity = new NotificationEventEntity();
        entity.setEventId(eventId.value());
        entity.setClientId("CLIENT_001");
        entity.setEventKey("test.event");
        entity.setContent("{}");
        entity.setCreatedAt(Instant.now());
        entity.setReceivedAt(Instant.now());
        entity.setStatus(DeliveryStatusEntity.PENDING);
        entity.setCycle(0);
        eventJpaRepository.save(entity);
    }

    private void saveDueAttempts(EventId eventId, int count, Instant dueAt) {
        IntStream.range(0, count).forEach(i -> adapter.save(new DeliveryAttempt(
            UUID.randomUUID(), eventId, 0, 1, dueAt, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        )));
    }

    private void createEventsWithDueAttempts(int count, Instant dueAt) {
        IntStream.range(0, count).forEach(i -> {
            var eventId = new EventId("evt-concurrent-" + i);
            createEvent(eventId);
            var attempt = new DeliveryAttempt(
                UUID.randomUUID(), eventId, 0, 1, dueAt, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
            );
            adapter.save(attempt);
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
}
