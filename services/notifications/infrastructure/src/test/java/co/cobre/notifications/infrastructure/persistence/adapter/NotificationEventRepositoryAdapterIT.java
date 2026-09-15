package co.cobre.notifications.infrastructure.persistence.adapter;

import co.cobre.notifications.application.query.ListNotificationEventsQuery;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.PersistenceTestSupport;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import co.cobre.notifications.infrastructure.persistence.jpa.NotificationEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationEventRepositoryAdapterIT extends PersistenceTestSupport {

    @Autowired
    private NotificationEventRepositoryAdapter adapter;

    @Autowired
    private NotificationEventJpaRepository jpaRepository;

    @Test
    void testSaveAndFindById() {
        var event = new NotificationEvent(
            new EventId("evt-001"),
            new ClientId("CLIENT001"),
            new EventKey("order.created"),
            "{\"order_id\": \"123\"}",
            Instant.parse("2024-01-10T10:00:00Z"),
            Instant.parse("2024-01-10T10:00:01Z"),
            DeliveryStatus.PENDING,
            Optional.of("sub-123"),
            0,
            Optional.empty()
        );

        adapter.save(event);
        var found = adapter.findById(new EventId("evt-001"));

        assertTrue(found.isPresent());
        assertEquals(event.eventId(), found.get().eventId());
        assertEquals(event.clientId(), found.get().clientId());
        assertEquals(event.status(), found.get().status());
    }

    @Test
    void testFindByClientAndId() {
        var event = new NotificationEvent(
            new EventId("evt-002"),
            new ClientId("CLIENT001"),
            new EventKey("payment.completed"),
            "{}",
            Instant.parse("2024-01-10T11:00:00Z"),
            Instant.parse("2024-01-10T11:00:01Z"),
            DeliveryStatus.COMPLETED,
            Optional.empty(),
            1,
            Optional.of(Instant.parse("2024-01-10T11:05:00Z"))
        );

        adapter.save(event);

        var found = adapter.findByClientAndId(
            new ClientId("CLIENT001"),
            new EventId("evt-002")
        );
        assertTrue(found.isPresent());

        var notFound = adapter.findByClientAndId(
            new ClientId("DIFFERENT_CLIENT"),
            new EventId("evt-002")
        );
        assertFalse(notFound.isPresent());
    }

    @Test
    void testExistsById() {
        var event = new NotificationEvent(
            new EventId("evt-003"),
            new ClientId("CLIENT001"),
            new EventKey("test.event"),
            "{}",
            Instant.now(),
            Instant.now(),
            DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );

        adapter.save(event);

        assertTrue(adapter.existsById(new EventId("evt-003")));
        assertFalse(adapter.existsById(new EventId("nonexistent")));
    }

    @Test
    void testTransitionUpdateStatusIfMatches() {
        var event = new NotificationEvent(
            new EventId("evt-004"),
            new ClientId("CLIENT001"),
            new EventKey("test"),
            "{}",
            Instant.now(),
            Instant.now(),
            DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );

        adapter.save(event);

        var updated = new NotificationEvent(
            new EventId("evt-004"),
            new ClientId("CLIENT001"),
            new EventKey("test"),
            "{}",
            Instant.now(),
            Instant.now(),
            DeliveryStatus.COMPLETED,
            Optional.empty(),
            1,
            Optional.of(Instant.parse("2024-01-10T12:00:00Z"))
        );

        boolean result = adapter.transition(
            new EventId("evt-004"),
            DeliveryStatus.PENDING,
            updated
        );

        assertTrue(result);

        var found = adapter.findById(new EventId("evt-004"));
        assertTrue(found.isPresent());
        assertEquals(DeliveryStatus.COMPLETED, found.get().status());
        assertEquals(1, found.get().cycle());
        assertTrue(found.get().deliveredAt().isPresent());
    }

    @Test
    void testTransitionReturnsFalseIfStatusMismatch() {
        var event = new NotificationEvent(
            new EventId("evt-005"),
            new ClientId("CLIENT001"),
            new EventKey("test"),
            "{}",
            Instant.now(),
            Instant.now(),
            DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );

        adapter.save(event);

        var updated = new NotificationEvent(
            new EventId("evt-005"),
            new ClientId("CLIENT001"),
            new EventKey("test"),
            "{}",
            Instant.now(),
            Instant.now(),
            DeliveryStatus.COMPLETED,
            Optional.empty(),
            0,
            Optional.empty()
        );

        boolean result = adapter.transition(
            new EventId("evt-005"),
            DeliveryStatus.COMPLETED,
            updated
        );

        assertFalse(result);

        var found = adapter.findById(new EventId("evt-005"));
        assertTrue(found.isPresent());
        assertEquals(DeliveryStatus.PENDING, found.get().status());
    }

    @Test
    void testSearchWithPaginationAndCursor() {
        var clientId = new ClientId("CLIENT_PAGINATED");
        var baseTime = Instant.now();

        for (int i = 0; i < 25; i++) {
            var event = new NotificationEvent(
                new EventId("evt-page-" + i),
                clientId,
                new EventKey("event.paginated"),
                "{}",
                baseTime.plusSeconds(i),
                baseTime.plusSeconds(i),
                DeliveryStatus.PENDING,
                Optional.empty(),
                0,
                Optional.empty()
            );
            adapter.save(event);
        }

        for (int i = 0; i < 5; i++) {
            var event = new NotificationEvent(
                new EventId("evt-other-" + i),
                new ClientId("OTHER_CLIENT"),
                new EventKey("event.other"),
                "{}",
                baseTime.plusSeconds(i),
                baseTime.plusSeconds(i),
                DeliveryStatus.PENDING,
                Optional.empty(),
                0,
                Optional.empty()
            );
            adapter.save(event);
        }

        var query = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            10,
            Optional.empty()
        );

        var page1 = adapter.search(query);

        assertEquals(10, page1.items().size());
        assertTrue(page1.nextCursor().isPresent());

        var query2 = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            10,
            page1.nextCursor()
        );

        var page2 = adapter.search(query2);

        assertEquals(10, page2.items().size());
        assertTrue(page2.nextCursor().isPresent());

        var query3 = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            10,
            page2.nextCursor()
        );

        var page3 = adapter.search(query3);

        assertEquals(5, page3.items().size());
        assertFalse(page3.nextCursor().isPresent());
    }

    @Test
    void testSearchFilterByStatus() {
        var clientId = new ClientId("CLIENT_STATUS");
        var now = Instant.now();

        for (int i = 0; i < 5; i++) {
            adapter.save(new NotificationEvent(
                new EventId("evt-pending-" + i),
                clientId,
                new EventKey("test"),
                "{}",
                now,
                now,
                DeliveryStatus.PENDING,
                Optional.empty(),
                0,
                Optional.empty()
            ));
        }

        for (int i = 0; i < 3; i++) {
            adapter.save(new NotificationEvent(
                new EventId("evt-failed-" + i),
                clientId,
                new EventKey("test"),
                "{}",
                now,
                now,
                DeliveryStatus.FAILED,
                Optional.empty(),
                0,
                Optional.empty()
            ));
        }

        var query = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.of(DeliveryStatus.FAILED),
            10,
            Optional.empty()
        );

        var page = adapter.search(query);

        assertEquals(3, page.items().size());
        assertTrue(page.items().stream().allMatch(e -> e.status() == DeliveryStatus.FAILED));
    }

    @Test
    void testSearchFilterByDateRange() {
        var clientId = new ClientId("CLIENT_DATE_RANGE");
        var start = Instant.parse("2024-01-10T00:00:00Z");
        var middle = start.plusSeconds(86400);
        var end = Instant.parse("2024-01-20T00:00:00Z");

        adapter.save(eventAt("evt-before", clientId, start.minusSeconds(100)));
        adapter.save(eventAt("evt-start", clientId, start));
        adapter.save(eventAt("evt-middle", clientId, middle));
        adapter.save(eventAt("evt-end", clientId, end));
        adapter.save(eventAt("evt-after", clientId, end.plusSeconds(100)));

        var query = new ListNotificationEventsQuery(
            clientId,
            Optional.of(start),
            Optional.of(end),
            Optional.empty(),
            10,
            Optional.empty()
        );

        var page = adapter.search(query);

        assertEquals(3, page.items().size());
    }

    @Test
    void testSearchNeverIncludesOtherClients() {
        var clientId = new ClientId("CLIENT_A");
        var otherClient = new ClientId("CLIENT_B");
        var now = Instant.now();

        for (int i = 0; i < 15; i++) {
            adapter.save(new NotificationEvent(
                new EventId("evt-a-" + i),
                clientId,
                new EventKey("test"),
                "{}",
                now,
                now,
                DeliveryStatus.PENDING,
                Optional.empty(),
                0,
                Optional.empty()
            ));
        }

        for (int i = 0; i < 5; i++) {
            adapter.save(new NotificationEvent(
                new EventId("evt-b-" + i),
                otherClient,
                new EventKey("test"),
                "{}",
                now,
                now,
                DeliveryStatus.PENDING,
                Optional.empty(),
                0,
                Optional.empty()
            ));
        }

        var query = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            20,
            Optional.empty()
        );

        var page = adapter.search(query);

        assertEquals(15, page.items().size());
        assertTrue(page.items().stream().allMatch(e -> e.clientId().equals(clientId)));
    }

    private NotificationEvent eventAt(String id, ClientId clientId, Instant createdAt) {
        return new NotificationEvent(
            new EventId(id),
            clientId,
            new EventKey("test"),
            "{}",
            createdAt,
            createdAt,
            DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );
    }
}
