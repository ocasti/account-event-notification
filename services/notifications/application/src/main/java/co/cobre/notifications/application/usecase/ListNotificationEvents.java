package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.UseCase;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.NotificationEventPage;
import co.cobre.notifications.application.usecase.NotificationEventSummary;
import co.cobre.notifications.application.usecase.NotificationEventSummaryPage;

import java.util.List;
import java.util.Map;

/**
 * Use case for listing notification events.
 */
@UseCase
public final class ListNotificationEvents {
    private final NotificationEventRepository events;
    private final DeliveryAttemptRepository attempts;

    /**
     * Creates a new list notification events use case.
     */
    public ListNotificationEvents(NotificationEventRepository events, DeliveryAttemptRepository attempts) {
        this.events = events;
        this.attempts = attempts;
    }

    /**
     * Lists notification events with attempts count for each event.
     */
    public NotificationEventSummaryPage list(ListNotificationEventsQuery query) {
        NotificationEventPage page = events.search(query);

        Map<co.cobre.notifications.domain.EventId, Integer> attemptCounts =
            page.items().isEmpty() ?
            Map.of() :
            attempts.countByEvents(
                page.items().stream()
                    .map(event -> event.eventId())
                    .toList()
            );

        List<NotificationEventSummary> summaries = page.items().stream()
            .map(event -> new NotificationEventSummary(
                event,
                attemptCounts.getOrDefault(event.eventId(), 0)
            ))
            .toList();

        return new NotificationEventSummaryPage(summaries, page.nextCursor());
    }
}
