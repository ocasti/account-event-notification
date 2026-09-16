package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryResult;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.infrastructure.persistence.PersistenceTestSupport;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptEntity;
import co.cobre.notifications.infrastructure.persistence.NotificationEventEntity;
import co.cobre.notifications.infrastructure.persistence.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptJpaRepository;
import co.cobre.notifications.infrastructure.persistence.NotificationEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testSaveAndFindByEvent() {
        var eventId = new EventId("evt-attempt-001");
        createEvent(eventId);

        var attempt = DeliveryAttempt.first(eventId, 0, Instant.now(), AttemptOrigin.SYSTEM);
        adapter.save(attempt);

        var found = adapter.findByEvent(eventId);

        assertEquals(1, found.size());
        assertEquals(attempt.id(), found.get(0).id());
    }

    @Test
    @Transactional
    void testFindByEventOrderedByCycleAndAttemptNumber() {
        var eventId = new EventId("evt-order-001");
        createEvent(eventId);

        var att1 = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 2, Instant.now(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var att2 = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 1, 1, Instant.now(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var att3 = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 1, Instant.now(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(att1);
        adapter.save(att2);
        adapter.save(att3);

        var found = adapter.findByEvent(eventId);

        assertEquals(3, found.size());
        assertEquals(0, found.get(0).cycle());
        assertEquals(1, found.get(0).attemptNumber());
        assertEquals(0, found.get(1).cycle());
        assertEquals(2, found.get(1).attemptNumber());
        assertEquals(1, found.get(2).cycle());
        assertEquals(1, found.get(2).attemptNumber());
    }

    @Test
    @Transactional
    void testClaimDueFiltersExecutedAttempts() {
        var eventId = new EventId("evt-executed-001");
        createEvent(eventId);

        var now = Instant.now();
        var pastDue = now.minusSeconds(10);

        var executeAttempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 1, pastDue, Optional.empty(), Optional.empty(),
            Optional.of(now), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(executeAttempt);

        var claim = new DeliveryClaim(now, 10, 10, "worker-1", Duration.ofSeconds(16));
        var claimed = adapter.claimDue(claim);

        assertEquals(0, claimed.size(), "Executed attempts should not be claimed");
    }

    @Test
    @Transactional
    void testClaimDueReturnsUnclaimed() {
        var eventId1 = new EventId("evt-claim-001");
        var eventId2 = new EventId("evt-claim-002");
        createEvent(eventId1);
        createEvent(eventId2);

        var now = Instant.now();
        var pastDue = now.minusSeconds(10);
        var future = now.plusSeconds(100);

        var duAttempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId1, 0, 1, pastDue, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var futureAttempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId1, 0, 2, future, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );
        var executeAttempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId2, 0, 1, pastDue, Optional.empty(), Optional.empty(),
            Optional.of(now), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(duAttempt);
        adapter.save(futureAttempt);
        adapter.save(executeAttempt);

        var claim = new DeliveryClaim(now, 10, 10, "worker-1", Duration.ofSeconds(16));
        var claimed = adapter.claimDue(claim);

        assertEquals(1, claimed.size(), "Expected 1 claimed attempt, but got " + claimed.size() +
            ". Claimed IDs: " + claimed.stream().map(DeliveryAttempt::id).toList());
        assertEquals(duAttempt.id(), claimed.get(0).id());
        assertTrue(claimed.get(0).claimedAt().isPresent());
        assertEquals("worker-1", claimed.get(0).claimedBy().get());
    }

    @Test
    @Transactional
    void testClaimDueRespectLeaseTimeout() {
        var eventId = new EventId("evt-lease-001");
        createEvent(eventId);

        var now = Instant.now();
        var pastDue = now.minusSeconds(30);
        var lease = Duration.ofSeconds(16);

        var att1 = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 1, pastDue, Optional.of(now.minusSeconds(15)),
            Optional.of("worker-a"), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), AttemptOrigin.SYSTEM
        );
        var att2 = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 2, pastDue, Optional.of(now.minusSeconds(20)),
            Optional.of("worker-b"), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(att1);
        adapter.save(att2);

        var claim = new DeliveryClaim(now, 10, 10, "worker-2", lease);
        var claimed = adapter.claimDue(claim);

        assertEquals(1, claimed.size());
        assertEquals(att2.id(), claimed.get(0).id());
    }

    @Test
    @Transactional
    void testClaimDueRespectMaxPerClient() {
        var eventId1 = new EventId("evt-max-001");
        var eventId2 = new EventId("evt-max-002");
        createEvent(eventId1);
        createEvent(eventId2);

        var now = Instant.now();
        var pastDue = now.minusSeconds(100);

        for (int i = 0; i < 8; i++) {
            var attempt = new DeliveryAttempt(
                java.util.UUID.randomUUID(),
                i < 4 ? eventId1 : eventId2,
                0, 1, pastDue, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
            );
            adapter.save(attempt);
        }

        var claim = new DeliveryClaim(now, 20, 5, "worker-1", Duration.ofSeconds(16));
        var claimed = adapter.claimDue(claim);

        assertEquals(5, claimed.size());
    }

    @Test
    @Transactional
    void testRecordResultIfSuccess() {
        var eventId = new EventId("evt-result-001");
        createEvent(eventId);

        var now = Instant.now();
        var attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 1, now,
            Optional.of(now.minusSeconds(5)), Optional.of("worker-1"),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(attempt);

        var result = new DeliveryResult(Optional.of(200), Optional.empty(), Optional.of(Duration.ofMillis(150)));
        var executed = attempt.executed(now.plusSeconds(1), "worker-1", result);

        boolean success = adapter.recordResultIf(executed, "worker-1");

        assertTrue(success);

        var found = adapter.findByEvent(eventId).get(0);
        assertTrue(found.executedAt().isPresent());
        assertEquals(200, found.responseStatus().get());
        assertEquals(150, found.latency().get().toMillis());
    }

    @Test
    @Transactional
    void testRecordResultIfFailsIfWrongWorker() {
        var eventId = new EventId("evt-wrong-worker-001");
        createEvent(eventId);

        var now = Instant.now();
        var attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 1, now,
            Optional.of(now.minusSeconds(5)), Optional.of("worker-1"),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
        );

        adapter.save(attempt);

        var result = new DeliveryResult(Optional.of(500), Optional.of("error"), Optional.of(Duration.ofMillis(100)));
        var executed = attempt.executed(now.plusSeconds(1), "worker-2", result);

        boolean success = adapter.recordResultIf(executed, "worker-2");

        assertFalse(success);

        var found = adapter.findByEvent(eventId).get(0);
        assertFalse(found.executedAt().isPresent());
    }

    @Test
    @Transactional
    void testRecordResultIfFailsIfAlreadyExecuted() {
        var eventId = new EventId("evt-executed-001");
        createEvent(eventId);

        var now = Instant.now();
        var attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(), eventId, 0, 1, now,
            Optional.of(now.minusSeconds(5)), Optional.of("worker-1"),
            Optional.of(now), Optional.of(200), Optional.empty(), Optional.of(Duration.ofMillis(50)), AttemptOrigin.SYSTEM
        );

        adapter.save(attempt);

        var result = new DeliveryResult(Optional.of(201), Optional.empty(), Optional.of(Duration.ofMillis(100)));
        var executed = attempt.executed(now.plusSeconds(1), "worker-1", result);

        boolean success = adapter.recordResultIf(executed, "worker-1");

        assertFalse(success);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testClaimDueConcurrency() throws InterruptedException {
        var baseTime = Instant.now();
        var pastDue = baseTime.minusSeconds(30);

        for (int i = 0; i < 20; i++) {
            var eventId = new EventId("evt-concurrent-" + i);
            createEvent(eventId);

            var attempt = new DeliveryAttempt(
                java.util.UUID.randomUUID(), eventId, 0, 1, pastDue, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), AttemptOrigin.SYSTEM
            );
            adapter.save(attempt);
        }

        Set<java.util.UUID> allClaimedIds = new HashSet<>();
        var latch = new CountDownLatch(4);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (int i = 0; i < 4; i++) {
            final String workerId = "worker-" + i;
            executor.submit(() -> {
                try {
                    var claim = new DeliveryClaim(baseTime, 20, Integer.MAX_VALUE, workerId, Duration.ofSeconds(16));
                    var claimed = adapter.claimDue(claim);
                    synchronized (allClaimedIds) {
                        allClaimedIds.addAll(claimed.stream().map(DeliveryAttempt::id).collect(Collectors.toSet()));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(20, allClaimedIds.size());
    }

    @Test
    @Transactional
    void testCountByEventsReturnsMapWithOnlyEventsHavingAttempts() {
        var eventId1 = new EventId("evt-count-001");
        var eventId2 = new EventId("evt-count-002");
        var eventId3 = new EventId("evt-count-003");
        createEvent(eventId1);
        createEvent(eventId2);
        createEvent(eventId3);

        var attempt1 = DeliveryAttempt.first(eventId1, 0, Instant.now(), co.cobre.notifications.domain.AttemptOrigin.SYSTEM);
        var attempt2 = DeliveryAttempt.first(eventId1, 0, Instant.now(), co.cobre.notifications.domain.AttemptOrigin.SYSTEM);
        var attempt3 = DeliveryAttempt.first(eventId1, 0, Instant.now(), co.cobre.notifications.domain.AttemptOrigin.SYSTEM);
        var attempt4 = DeliveryAttempt.first(eventId2, 0, Instant.now(), co.cobre.notifications.domain.AttemptOrigin.SYSTEM);

        adapter.save(attempt1);
        adapter.save(attempt2);
        adapter.save(attempt3);
        adapter.save(attempt4);

        var counts = adapter.countByEvents(java.util.List.of(eventId1, eventId2, eventId3));

        assertEquals(2, counts.size());
        assertEquals(3, counts.get(eventId1));
        assertEquals(1, counts.get(eventId2));
        assertFalse(counts.containsKey(eventId3));
    }

    @Test
    @Transactional
    void testCountByEventsWithEmptyListReturnsEmptyMap() {
        var counts = adapter.countByEvents(java.util.List.of());

        assertEquals(0, counts.size());
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
}
