package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListNotificationEventsTest {
    @Mock
    NotificationEventRepository events;

    @Mock
    DeliveryAttemptRepository attempts;

    static Stream<Arguments> attemptsCountCases() {
        return Stream.of(
            Arguments.of("event has recorded attempts", Map.of(Ids.EVT_001, 3), 3),
            Arguments.of("event has no recorded attempts", Map.of(), 0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attemptsCountCases")
    void shouldAttachAttemptsCountWhenPageIsReturned(String caseLabel, Map<EventId, Integer> countsByEvent, int expectedCount) {
        var useCase = new ListNotificationEvents(events, attempts);
        var query = new ListNotificationEventsQuery(
            Ids.CLIENT_001, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        );
        NotificationEvent skippedEvent = NotificationEvents.skipped();
        var expectedPage = new NotificationEventPage(List.of(skippedEvent), Optional.empty());
        when(events.search(query)).thenReturn(expectedPage);
        when(attempts.countByEvents(List.of(Ids.EVT_001))).thenReturn(countsByEvent);

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).event()).isEqualTo(skippedEvent);
        assertThat(result.items().get(0).attemptsCount()).isEqualTo(expectedCount);
        assertThat(result.nextCursor()).isEmpty();
        verify(events).search(query);
        verify(attempts).countByEvents(List.of(Ids.EVT_001));
    }

    @Test
    void shouldSkipAttemptsLookupWhenPageIsEmpty() {
        var useCase = new ListNotificationEvents(events, attempts);
        var query = new ListNotificationEventsQuery(
            Ids.CLIENT_001, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        );
        var emptyPage = new NotificationEventPage(List.of(), Optional.empty());
        when(events.search(query)).thenReturn(emptyPage);

        NotificationEventSummaryPage result = useCase.list(query);

        assertThat(result.items()).isEmpty();
        verify(events).search(query);
        verifyNoInteractions(attempts);
    }
}
