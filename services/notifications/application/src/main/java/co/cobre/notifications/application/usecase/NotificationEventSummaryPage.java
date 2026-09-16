package co.cobre.notifications.application.usecase;

import java.util.List;
import java.util.Optional;

public record NotificationEventSummaryPage(
    List<NotificationEventSummary> items,
    Optional<String> nextCursor
) {}
