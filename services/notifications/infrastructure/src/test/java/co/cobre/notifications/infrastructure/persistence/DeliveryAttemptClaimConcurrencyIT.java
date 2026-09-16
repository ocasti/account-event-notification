package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryAttemptClaimConcurrencyIT extends PersistenceTestSupport {

    @Autowired
    private DeliveryAttemptRepositoryAdapter adapter;

    @Autowired
    private NotificationEventJpaRepository eventJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanupTestData() {
        jdbcTemplate.execute("DELETE FROM delivery_attempts");
        jdbcTemplate.execute("DELETE FROM notification_events");
    }

    @RepeatedTest(3)
    void testConcurrentClaimsNeverOverlap() throws InterruptedException {
        int totalAttempts = 200;
        int numClients = 10;
        Instant baseTime = Instant.now();
        Instant pastDue = baseTime.minusSeconds(30);

        List<UUID> createdIds = new ArrayList<>();
        for (int i = 0; i < totalAttempts; i++) {
            String clientId = "client-" + (i % numClients);
            EventId eventId = new EventId("evt-concurrent-" + i);

            NotificationEventEntity event = new NotificationEventEntity();
            event.setEventId(eventId.value());
            event.setClientId(clientId);
            event.setEventKey("test.event");
            event.setContent("{}");
            event.setCreatedAt(baseTime);
            event.setReceivedAt(baseTime);
            event.setStatus(DeliveryStatusEntity.PENDING);
            event.setCycle(0);
            eventJpaRepository.save(event);

            DeliveryAttempt attempt = DeliveryAttempt.first(
                eventId, 0, pastDue, AttemptOrigin.SYSTEM
            );
            adapter.save(attempt);
            createdIds.add(attempt.id());
        }

        int numWorkers = 8;
        int roundsPerWorker = 5;
        CyclicBarrier barrier = new CyclicBarrier(numWorkers);
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

        // Track which worker claimed each id (to detect duplicates)
        ConcurrentHashMap<UUID, String> idToWorker = new ConcurrentHashMap<>();
        AtomicInteger totalClaimed = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        for (int w = 0; w < numWorkers; w++) {
            final String workerId = "worker-" + w;
            executor.submit(() -> {
                try {
                    barrier.await(); // Synchronize all workers at start
                    for (int round = 0; round < roundsPerWorker; round++) {
                        DeliveryClaim claim = new DeliveryClaim(
                            baseTime, 50, 50, workerId, Duration.ofSeconds(3600)
                        );
                        List<DeliveryAttempt> claimed = adapter.claimDue(claim);

                        for (DeliveryAttempt attempt : claimed) {
                            UUID id = attempt.id();
                            String previous = idToWorker.putIfAbsent(id, workerId);
                            if (previous != null && !previous.equals(workerId)) {
                                fail("Duplicate claim detected: id=" + id +
                                    " claimed by both " + previous + " and " + workerId);
                            }
                            totalClaimed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            });
        }

        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            fail("Executor did not finish in time");
        }

        if (!exceptions.isEmpty()) {
            fail("Exceptions during concurrent claims: " + exceptions);
        }

        assertEquals(totalAttempts, idToWorker.size(),
            "Expected all " + totalAttempts + " attempts to be claimed without duplicates between workers, " +
            "but got " + idToWorker.size() + " unique ids. " +
            "Total claims across all rounds: " + totalClaimed.get());
    }
}
