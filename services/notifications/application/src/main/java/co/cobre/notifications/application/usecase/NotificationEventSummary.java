package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.NotificationEvent;

public record NotificationEventSummary(
    NotificationEvent event,
    int attemptsCount
) {}
