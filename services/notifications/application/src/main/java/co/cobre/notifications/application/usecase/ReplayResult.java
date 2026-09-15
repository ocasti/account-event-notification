package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.EventId;

/**
 * Result of replaying a notification event.
 */
public record ReplayResult(
    EventId eventId,
    int cycle
) {}
