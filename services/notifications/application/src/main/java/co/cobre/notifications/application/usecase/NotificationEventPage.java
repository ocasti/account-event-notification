package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.NotificationEvent;

import java.util.List;
import java.util.Optional;

/**
 * A page of notification events with optional cursor for pagination.
 */
public record NotificationEventPage(
    List<NotificationEvent> items,
    Optional<String> nextCursor
) {}
