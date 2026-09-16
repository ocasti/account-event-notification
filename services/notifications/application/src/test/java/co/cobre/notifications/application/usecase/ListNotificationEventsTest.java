package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.NotificationEventPage;
import co.cobre.notifications.application.usecase.NotificationEventSummaryPage;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventData;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ListNotificationEventsTest {
    @Mock
    NotificationEventRepository events;

    @Mock
    DeliveryAttemptRepository attempts;

    @Test
    void shouldAdjoinAttemptsCountAndReturnSummaryPage() {
        ListNotificationEvents useCase = new ListNotificationEvents(events, attempts);

        ClientId clientId = new ClientId("client-1");
        Instant from = Instant.parse("2025-01-01T10:00:00Z");
        Instant to = Instant.parse("2025-01-01T12:00:00Z");

        ListNotificationEventsQuery query = new ListNotificationEventsQuery(
            clientId,
            Optional.of(from),
            Optional.of(to),
            Optional.of(DeliveryStatus.PENDING),
            50,
            Optional.empty()
        );

        EventId eventId1 = new EventId("evt-1");
        var data = new EventData(
            eventId1,
            clientId,
            new co.cobre.notifications.domain.EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z")
        );
        NotificationEvent event1 = NotificationEvent.skipped(
            data,
            Instant.parse("2025-01-01T11:00:01Z")
        );

        NotificationEventPage expectedPage = new NotificationEventPage(
            List.of(event1),
            Optional.empty()
        );

        when(events.search(query)).thenReturn(expectedPage);
        when(attempts.countByEvents(List.of(eventId1))).thenReturn(Map.of(eventId1, 3));

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).event()).isEqualTo(event1);
        assertThat(result.items().get(0).attemptsCount()).isEqualTo(3);
        assertThat(result.nextCursor()).isEmpty();
        verify(events).search(query);
        verify(attempts).countByEvents(List.of(eventId1));
    }

    @Test
    void shouldUseZeroCountForEventWithNoAttempts() {
        ListNotificationEvents useCase = new ListNotificationEvents(events, attempts);

        ClientId clientId = new ClientId("client-1");
        ListNotificationEventsQuery query = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            20,
            Optional.empty()
        );

        EventId eventId1 = new EventId("evt-1");
        var data = new EventData(
            eventId1,
            clientId,
            new co.cobre.notifications.domain.EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z")
        );
        NotificationEvent event1 = NotificationEvent.skipped(
            data,
            Instant.parse("2025-01-01T11:00:01Z")
        );

        NotificationEventPage expectedPage = new NotificationEventPage(
            List.of(event1),
            Optional.empty()
        );

        when(events.search(query)).thenReturn(expectedPage);
        when(attempts.countByEvents(List.of(eventId1))).thenReturn(Map.of());

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items().get(0).attemptsCount()).isEqualTo(0);
    }

    @Test
    void shouldNotQueryAttemptsWhenPageIsEmpty() {
        ListNotificationEvents useCase = new ListNotificationEvents(events, attempts);

        ClientId clientId = new ClientId("client-1");
        ListNotificationEventsQuery query = new ListNotificationEventsQuery(
            clientId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            20,
            Optional.empty()
        );

        NotificationEventPage emptyPage = new NotificationEventPage(
            List.of(),
            Optional.empty()
        );

        when(events.search(query)).thenReturn(emptyPage);

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items()).isEmpty();
        verify(events).search(query);
        verify(attempts, never()).countByEvents(anyCollection());
    }
}
