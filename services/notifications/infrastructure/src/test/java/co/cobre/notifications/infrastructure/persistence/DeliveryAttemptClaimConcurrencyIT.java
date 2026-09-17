package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.infrastructure.fixtures.Entities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryAttemptClaimConcurrencyIT extends PersistenceTestSupport {

    private static final int TOTAL_ATTEMPTS = 200;
    private static final int NUM_CLIENTS = 10;
    private static final int NUM_WORKERS = 8;
    private static final int ROUNDS_PER_WORKER = 5;

    /**
     * {@code notification_events.subscription_id} has a foreign key on {@code subscriptions};
     * this is one of the three rows Flyway seeds (V2__initial_subscriptions.sql), used here only
     * to satisfy that constraint — this test does not assert on subscription identity.
     */
    private static final String PERSISTED_SUBSCRIPTION_ID = "sub_client001";

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
    void shouldClaimEachAttemptExactlyOnceWhenWorkersCompeteAcrossMultipleRounds() throws InterruptedException {
        insertPendingAttempts(TOTAL_ATTEMPTS, NUM_CLIENTS, secondsAgo(30));

        var result = claimConcurrently(NUM_WORKERS, ROUNDS_PER_WORKER);

        assertThat(result.executorFinishedInTime()).isTrue();
        assertThat(result.workerFailures()).isEmpty();
        assertThat(result.duplicateClaims()).isEmpty();
        assertThat(result.claimedByAttemptId()).hasSize(TOTAL_ATTEMPTS);
    }

    /**
     * {@code claimDueAttempts} filters on Postgres's own {@code now()}, so "past due" must be
     * expressed relative to the real wall clock rather than the fixed {@link Clocks#NOW}.
     */
    private Instant secondsAgo(long seconds) {
        return Instant.now().minusSeconds(seconds);
    }

    private void insertPendingAttempts(int totalAttempts, int numClients, Instant pastDue) {
        IntStream.range(0, totalAttempts).forEach(i -> {
            var clientId = new ClientId("client-" + (i % numClients));
            var eventId = new EventId("evt-concurrent-" + i);

            var event = NotificationEvents.aPendingEvent()
                .withEventId(eventId)
                .withClientId(clientId)
                .withEventKey(new EventKey("test.event"))
                .withCreatedAt(Clocks.NOW)
                .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
                .build();
            eventJpaRepository.save(Entities.notificationEvent(event));

            var attempt = DeliveryAttempt.first(eventId, 0, pastDue, AttemptOrigin.SYSTEM);
            adapter.save(attempt);
        });
    }

    private record ConcurrentClaimResult(
        Map<UUID, String> claimedByAttemptId,
        List<String> duplicateClaims,
        List<Exception> workerFailures,
        boolean executorFinishedInTime
    ) {
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private ConcurrentClaimResult claimConcurrently(int numWorkers, int roundsPerWorker)
        throws InterruptedException {
        var barrier = new CyclicBarrier(numWorkers);
        var executor = Executors.newFixedThreadPool(numWorkers);
        var claimedByAttemptId = new ConcurrentHashMap<UUID, String>();
        var duplicateClaims = new CopyOnWriteArrayList<String>();
        var workerFailures = new CopyOnWriteArrayList<Exception>();

        IntStream.range(0, numWorkers).forEach(w -> {
            var workerId = "worker-" + w;
            executor.submit(() -> runClaimRounds(
                workerId, roundsPerWorker, barrier, claimedByAttemptId, duplicateClaims, workerFailures
            ));
        });

        executor.shutdown();
        var finishedInTime = executor.awaitTermination(30, TimeUnit.SECONDS);

        return new ConcurrentClaimResult(claimedByAttemptId, duplicateClaims, workerFailures, finishedInTime);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void runClaimRounds(
        String workerId,
        int roundsPerWorker,
        CyclicBarrier barrier,
        Map<UUID, String> claimedByAttemptId,
        List<String> duplicateClaims,
        List<Exception> workerFailures
    ) {
        try {
            barrier.await();
            IntStream.range(0, roundsPerWorker).forEach(round -> {
                var claim = new DeliveryClaim(Clocks.NOW, 50, 50, workerId, Duration.ofSeconds(3600));
                var claimed = adapter.claimDue(claim);
                claimed.forEach(attempt -> recordClaim(attempt.id(), workerId, claimedByAttemptId, duplicateClaims));
            });
        } catch (Exception e) {
            workerFailures.add(e);
        }
    }

    private void recordClaim(UUID id, String workerId, Map<UUID, String> claimedByAttemptId, List<String> duplicateClaims) {
        var previous = claimedByAttemptId.putIfAbsent(id, workerId);
        var claimedByAnotherWorker = previous != null && !previous.equals(workerId);
        if (claimedByAnotherWorker) {
            duplicateClaims.add("Duplicate claim detected: id=" + id + " claimed by both " + previous + " and " + workerId);
        }
    }
}
