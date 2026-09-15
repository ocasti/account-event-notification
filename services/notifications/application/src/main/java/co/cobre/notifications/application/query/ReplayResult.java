package co.cobre.notifications.application.query;

import co.cobre.notifications.domain.model.EventId;

/**
 * Result of replaying a notification event.
 */
public record ReplayResult(
    EventId eventId,
    int cycle
) {}
