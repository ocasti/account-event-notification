package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.EventId;

public record ReplayResult(
    EventId eventId,
    int cycle
) {}
