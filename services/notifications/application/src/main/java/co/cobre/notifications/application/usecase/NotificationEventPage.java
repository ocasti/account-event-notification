package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.NotificationEvent;

import java.util.List;
import java.util.Optional;

public record NotificationEventPage(
    List<NotificationEvent> items,
    Optional<String> nextCursor
) {}
