package co.cobre.notifications.application.query;

import co.cobre.notifications.domain.model.NotificationEvent;

import java.util.List;
import java.util.Optional;

/**
 * A page of notification events with optional cursor for pagination.
 */
public record NotificationEventPage(
    List<NotificationEvent> items,
    Optional<String> nextCursor
) {}
