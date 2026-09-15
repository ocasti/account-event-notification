package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.entity.AttemptOriginEntity;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryAttemptEntity;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import co.cobre.notifications.infrastructure.persistence.jpa.DeliveryAttemptJpaRepository;
import co.cobre.notifications.infrastructure.persistence.jpa.NotificationEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptJpaRepositoryIT extends PersistenceTestSupport {

    @Autowired
    private DeliveryAttemptJpaRepository deliveryAttemptRepository;

    @Autowired
    private NotificationEventJpaRepository notificationEventRepository;

    @Test
    void countDue_returnsOnlyUnexecutedUnclaimedAttemptsDueNow() {
        var event = new NotificationEventEntity();
        event.setEventId("test-event-1");
        event.setClientId("test-client");
        event.setEventKey("test.key");
        event.setContent("{}");
        event.setCreatedAt(Instant.now());
        event.setReceivedAt(Instant.now());
        event.setStatus(DeliveryStatusEntity.PENDING);
        event.setCycle(0);
        notificationEventRepository.save(event);

        var now = Instant.now();
        var past = now.minus(1, ChronoUnit.HOURS);
        var future = now.plus(1, ChronoUnit.HOURS);

        var dueAttempt = new DeliveryAttemptEntity();
        dueAttempt.setId(UUID.randomUUID());
        dueAttempt.setEventId("test-event-1");
        dueAttempt.setCycle(0);
        dueAttempt.setAttemptNumber(1);
        dueAttempt.setNextAttemptAt(past);
        dueAttempt.setOrigin(AttemptOriginEntity.SYSTEM);
        deliveryAttemptRepository.save(dueAttempt);

        var claimedAttempt = new DeliveryAttemptEntity();
        claimedAttempt.setId(UUID.randomUUID());
        claimedAttempt.setEventId("test-event-1");
        claimedAttempt.setCycle(0);
        claimedAttempt.setAttemptNumber(1);
        claimedAttempt.setNextAttemptAt(past);
        claimedAttempt.setClaimedAt(now);
        claimedAttempt.setClaimedBy("worker-1");
        claimedAttempt.setOrigin(AttemptOriginEntity.SYSTEM);
        deliveryAttemptRepository.save(claimedAttempt);

        var futureAttempt = new DeliveryAttemptEntity();
        futureAttempt.setId(UUID.randomUUID());
        futureAttempt.setEventId("test-event-1");
        futureAttempt.setCycle(0);
        futureAttempt.setAttemptNumber(1);
        futureAttempt.setNextAttemptAt(future);
        futureAttempt.setOrigin(AttemptOriginEntity.SYSTEM);
        deliveryAttemptRepository.save(futureAttempt);

        var executedAttempt = new DeliveryAttemptEntity();
        executedAttempt.setId(UUID.randomUUID());
        executedAttempt.setEventId("test-event-1");
        executedAttempt.setCycle(0);
        executedAttempt.setAttemptNumber(1);
        executedAttempt.setNextAttemptAt(past);
        executedAttempt.setExecutedAt(now);
        executedAttempt.setResponseStatus(200);
        executedAttempt.setLatencyMs(100L);
        executedAttempt.setOrigin(AttemptOriginEntity.SYSTEM);
        deliveryAttemptRepository.save(executedAttempt);

        long result = deliveryAttemptRepository.countDue();

        assertThat(result).isEqualTo(1L);
    }
}
