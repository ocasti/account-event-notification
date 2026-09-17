package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;

@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.flyway.placeholders.webhookUrl=https://example.test/webhook", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationEventRepositoryAdapter.class, DeliveryAttemptRepositoryAdapter.class, SubscriptionRepositoryAdapter.class, NotificationEventEntityMapper.class, DeliveryAttemptEntityMapper.class, SubscriptionEntityMapper.class})
public abstract class PersistenceTestSupport {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withReuse(false);

    static {
        POSTGRES.start();
    }

    /**
     * Saves {@code count} notification events for the given client, named
     * {@code idPrefix + i} (i from 0 inclusive to count exclusive), all sharing
     * the given event key, status and creation instant offset by i seconds.
     * Centralizes the bulk-insert loop so {@code @Test} bodies stay
     * control-flow free.
     */
    protected static void savePendingEvents(
        NotificationEventRepositoryAdapter adapter,
        int count,
        String idPrefix,
        ClientId clientId,
        EventKey eventKey,
        DeliveryStatus status,
        Instant baseTime
    ) {
        IntStream.range(0, count).forEach(i -> adapter.save(new NotificationEvent(
            new EventId(idPrefix + i),
            clientId,
            eventKey,
            "{}",
            baseTime.plusSeconds(i),
            baseTime.plusSeconds(i),
            status,
            Optional.empty(),
            0,
            Optional.empty()
        )));
    }
}
