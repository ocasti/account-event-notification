package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.NotificationEvent;

/**
 * A summary of a notification event with its delivery attempts count.
 */
public record NotificationEventSummary(
    NotificationEvent event,
    int attemptsCount
) {}
