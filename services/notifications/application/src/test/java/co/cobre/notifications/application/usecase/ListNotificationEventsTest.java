package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventData;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListNotificationEventsTest {
    @Mock
    NotificationEventRepository events;

    @Mock
    DeliveryAttemptRepository attempts;

    private NotificationEvent skippedEvent(EventId eventId, ClientId clientId) {
        var data = new EventData(
            eventId, clientId, new EventKey("order.created"), "Order created", Instant.parse("2025-01-01T11:00:00Z")
        );
        return NotificationEvent.skipped(data, Instant.parse("2025-01-01T11:00:01Z"));
    }

    static Stream<Arguments> attemptsCountCases() {
        EventId eventId1 = new EventId("evt-1");
        return Stream.of(
            Arguments.of("event has recorded attempts", Map.of(eventId1, 3), 3),
            Arguments.of("event has no recorded attempts", Map.of(), 0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attemptsCountCases")
    void shouldAttachAttemptsCountWhenPageIsReturned(String caseLabel, Map<EventId, Integer> countsByEvent, int expectedCount) {
        ListNotificationEvents useCase = new ListNotificationEvents(events, attempts);
        ClientId clientId = new ClientId("client-1");
        ListNotificationEventsQuery query = new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        );
        EventId eventId1 = new EventId("evt-1");
        NotificationEvent event1 = skippedEvent(eventId1, clientId);
        NotificationEventPage expectedPage = new NotificationEventPage(List.of(event1), Optional.empty());
        when(events.search(query)).thenReturn(expectedPage);
        when(attempts.countByEvents(List.of(eventId1))).thenReturn(countsByEvent);

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).event()).isEqualTo(event1);
        assertThat(result.items().get(0).attemptsCount()).isEqualTo(expectedCount);
        assertThat(result.nextCursor()).isEmpty();
        verify(events).search(query);
        verify(attempts).countByEvents(List.of(eventId1));
    }

    @Test
    void shouldSkipAttemptsLookupWhenPageIsEmpty() {
        ListNotificationEvents useCase = new ListNotificationEvents(events, attempts);
        ClientId clientId = new ClientId("client-1");
        ListNotificationEventsQuery query = new ListNotificationEventsQuery(
            clientId, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        );
        NotificationEventPage emptyPage = new NotificationEventPage(List.of(), Optional.empty());
        when(events.search(query)).thenReturn(emptyPage);

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items()).isEmpty();
        verify(events).search(query);
        verify(attempts, never()).countByEvents(anyCollection());
    }
}
