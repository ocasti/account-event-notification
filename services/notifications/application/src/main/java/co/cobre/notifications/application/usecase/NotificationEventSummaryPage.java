package co.cobre.notifications.application.usecase;

import java.util.List;
import java.util.Optional;

/**
 * A page of notification event summaries with optional cursor for pagination.
 */
public record NotificationEventSummaryPage(
    List<NotificationEventSummary> items,
    Optional<String> nextCursor
) {}
