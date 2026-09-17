package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventRepositoryAdapterIT extends PersistenceTestSupport {

    /**
     * {@code notification_events.subscription_id} has a foreign key on {@code subscriptions};
     * this is one of the three rows Flyway seeds (V2__initial_subscriptions.sql), used here only
     * to satisfy that constraint — none of these tests assert on subscription identity.
     */
    private static final String PERSISTED_SUBSCRIPTION_ID = "sub_client001";

    @Autowired
    private NotificationEventRepositoryAdapter adapter;

    @Autowired
    private NotificationEventJpaRepository jpaRepository;

    @Test
    void shouldFindSavedEventByIdWhenEventIsSaved() {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-001"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("order.created"))
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();

        adapter.save(event);
        var found = adapter.findById(new EventId("evt-001"));

        assertThat(found).isPresent();
        assertThat(found.get().eventId()).isEqualTo(event.eventId());
        assertThat(found.get().clientId()).isEqualTo(event.clientId());
        assertThat(found.get().status()).isEqualTo(event.status());
    }

    @Test
    void shouldFindEventOnlyForMatchingClientWhenFindingByClientAndId() {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-002"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("payment.completed"))
            .withStatus(DeliveryStatus.COMPLETED)
            .withCycle(1)
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();

        adapter.save(event);
        var found = adapter.findByClientAndId(Ids.CLIENT_001, new EventId("evt-002"));
        var notFound = adapter.findByClientAndId(new ClientId("DIFFERENT_CLIENT"), new EventId("evt-002"));

        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    @Test
    void shouldReportExistenceCorrectlyWhenCheckingExistsById() {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-003"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("test.event"))
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();

        adapter.save(event);
        var existing = adapter.existsById(new EventId("evt-003"));
        var missing = adapter.existsById(new EventId("nonexistent"));

        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    void shouldUpdateStatusWhenExpectedStatusMatches() {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-004"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("test"))
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();
        var updated = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-004"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("test"))
            .withStatus(DeliveryStatus.COMPLETED)
            .withCycle(1)
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();

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
        var event = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-005"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("test"))
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();
        var updated = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-005"))
            .withClientId(Ids.CLIENT_001)
            .withEventKey(new EventKey("test"))
            .withStatus(DeliveryStatus.COMPLETED)
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();

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
        savePendingEvents(adapter, 25, "evt-page-", clientId, new EventKey("event.paginated"), DeliveryStatus.PENDING, Clocks.NOW);
        savePendingEvents(adapter, 5, "evt-other-", new ClientId("OTHER_CLIENT"), new EventKey("event.other"), DeliveryStatus.PENDING, Clocks.NOW);

        var firstPage = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 10, Optional.empty()
        ));
        var secondPage = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 10, firstPage.nextCursor()
        ));
        var lastPage = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 10, secondPage.nextCursor()
        ));

        assertThat(firstPage.items()).hasSize(10);
        assertThat(firstPage.nextCursor()).isPresent();
        assertThat(secondPage.items()).hasSize(10);
        assertThat(secondPage.nextCursor()).isPresent();
        assertThat(lastPage.items()).hasSize(5);
        assertThat(lastPage.nextCursor()).isEmpty();
    }

    @Test
    void shouldReturnOnlyMatchingStatusEventsWhenFilteringByStatus() {
        var clientId = new ClientId("CLIENT_STATUS");
        savePendingEvents(adapter, 5, "evt-pending-", clientId, new EventKey("test"), DeliveryStatus.PENDING, Clocks.NOW);
        savePendingEvents(adapter, 3, "evt-failed-", clientId, new EventKey("test"), DeliveryStatus.FAILED, Clocks.NOW);

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
        savePendingEvents(adapter, 15, "evt-a-", clientId, new EventKey("test"), DeliveryStatus.PENDING, Clocks.NOW);
        savePendingEvents(adapter, 5, "evt-b-", otherClient, new EventKey("test"), DeliveryStatus.PENDING, Clocks.NOW);

        var page = adapter.search(new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        ));

        assertThat(page.items())
            .hasSize(15)
            .extracting(NotificationEvent::clientId)
            .containsOnly(clientId);
    }

    private NotificationEvent eventAt(String id, ClientId clientId, Instant createdAt) {
        return NotificationEvents.aPendingEvent()
            .withEventId(new EventId(id))
            .withClientId(clientId)
            .withEventKey(new EventKey("test"))
            .withCreatedAt(createdAt)
            .withSubscriptionId(PERSISTED_SUBSCRIPTION_ID)
            .build();
    }
}
