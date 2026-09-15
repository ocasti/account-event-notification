package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.query.ListNotificationEventsQuery;
import co.cobre.notifications.application.query.NotificationEventPage;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListNotificationEventsTest {
    @Mock
    NotificationEventRepository events;

    @Test
    void shouldDelegateSearchAndReturnPage() {
        ListNotificationEvents useCase = new ListNotificationEvents(events);

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

        NotificationEvent event1 = NotificationEvent.skipped(
            new co.cobre.notifications.domain.model.EventId("evt-1"),
            clientId,
            new co.cobre.notifications.domain.model.EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z")
        );

        NotificationEventPage expectedPage = new NotificationEventPage(
            List.of(event1),
            Optional.empty()
        );

        when(events.search(query)).thenReturn(expectedPage);

        NotificationEventPage result = useCase.list(query);

        assertThat(result).isEqualTo(expectedPage);
        assertThat(result.items()).hasSize(1);
        verify(events).search(query);
    }
}
