package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventRepositoryAdapterIT extends PersistenceTestSupport {

    @Autowired
    private NotificationEventRepositoryAdapter adapter;

    @Autowired
    private NotificationEventJpaRepository jpaRepository;

    @Test
    void shouldFindSavedEventByIdWhenEventIsSaved() {
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

        assertThat(found).isPresent();
        assertThat(found.get().eventId()).isEqualTo(event.eventId());
        assertThat(found.get().clientId()).isEqualTo(event.clientId());
        assertThat(found.get().status()).isEqualTo(event.status());
    }

    @Test
    void shouldFindEventOnlyForMatchingClientWhenFindingByClientAndId() {
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
        var found = adapter.findByClientAndId(new ClientId("CLIENT001"), new EventId("evt-002"));
        var notFound = adapter.findByClientAndId(new ClientId("DIFFERENT_CLIENT"), new EventId("evt-002"));

        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    @Test
    void shouldReportExistenceCorrectlyWhenCheckingExistsById() {
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
        var existing = adapter.existsById(new EventId("evt-003"));
        var missing = adapter.existsById(new EventId("nonexistent"));

        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    void shouldUpdateStatusWhenExpectedStatusMatches() {
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

        adapter.save(event);
        var result = adapter.transition(new EventId("evt-004"), DeliveryStatus.PENDING, updated);

        assertThat(result).isTrue();
        var found = adapter.findById(new EventId("evt-004"));
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(found.get().cycle()).isEqualTo(1);
        assertThat(found.get().deliveredAt()).isPresent();
    }

    @Test
    void shouldNotUpdateStatusWhenExpectedStatusMismatches() {
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

        adapter.save(event);
        var result = adapter.transition(new EventId("evt-005"), DeliveryStatus.COMPLETED, updated);

        assertThat(result).isFalse();
        var found = adapter.findById(new EventId("evt-005"));
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void shouldPaginateAcrossCursorsWhenSearchingWithPageSizeLimit() {
        var clientId = new ClientId("CLIENT_PAGINATED");
        var baseTime = Instant.now();
        savePendingEvents(adapter, 25, "evt-page-", clientId, new EventKey("event.paginated"), DeliveryStatus.PENDING, baseTime);
        savePendingEvents(adapter, 5, "evt-other-", new ClientId("OTHER_CLIENT"), new EventKey("event.other"), DeliveryStatus.PENDING, baseTime);

        var page1 = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 10, Optional.empty()
        ));
        var page2 = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 10, page1.nextCursor()
        ));
        var page3 = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 10, page2.nextCursor()
        ));

        assertThat(page1.items()).hasSize(10);
        assertThat(page1.nextCursor()).isPresent();
        assertThat(page2.items()).hasSize(10);
        assertThat(page2.nextCursor()).isPresent();
        assertThat(page3.items()).hasSize(5);
        assertThat(page3.nextCursor()).isEmpty();
    }

    @Test
    void shouldReturnOnlyMatchingStatusEventsWhenFilteringByStatus() {
        var clientId = new ClientId("CLIENT_STATUS");
        var now = Instant.now();
        savePendingEvents(adapter, 5, "evt-pending-", clientId, new EventKey("test"), DeliveryStatus.PENDING, now);
        savePendingEvents(adapter, 3, "evt-failed-", clientId, new EventKey("test"), DeliveryStatus.FAILED, now);

        var page = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.of(DeliveryStatus.FAILED), 10, Optional.empty()
        ));

        assertThat(page.items())
            .hasSize(3)
            .extracting(NotificationEvent::status)
            .containsOnly(DeliveryStatus.FAILED);
    }

    @Test
    void shouldFilterEventsWithinDateRangeWhenSearching() {
        var clientId = new ClientId("CLIENT_DATE_RANGE");
        var start = Instant.parse("2024-01-10T00:00:00Z");
        var middle = start.plusSeconds(86400);
        var end = Instant.parse("2024-01-20T00:00:00Z");
        adapter.save(eventAt("evt-before", clientId, start.minusSeconds(100)));
        adapter.save(eventAt("evt-start", clientId, start));
        adapter.save(eventAt("evt-middle", clientId, middle));
        adapter.save(eventAt("evt-end", clientId, end));
        adapter.save(eventAt("evt-after", clientId, end.plusSeconds(100)));

        var page = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.of(start), Optional.of(end), Optional.empty(), 10, Optional.empty()
        ));

        assertThat(page.items()).hasSize(3);
    }

    @Test
    void shouldExcludeOtherClientsWhenSearching() {
        var clientId = new ClientId("CLIENT_A");
        var otherClient = new ClientId("CLIENT_B");
        var now = Instant.now();
        savePendingEvents(adapter, 15, "evt-a-", clientId, new EventKey("test"), DeliveryStatus.PENDING, now);
        savePendingEvents(adapter, 5, "evt-b-", otherClient, new EventKey("test"), DeliveryStatus.PENDING, now);

        var page = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        ));

        assertThat(page.items())
            .hasSize(15)
            .extracting(NotificationEvent::clientId)
            .containsOnly(clientId);
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
