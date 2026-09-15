package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;

import java.time.Instant;

/**
 * Command to register a new notification event.
 */
public record RegisterEventCommand(
    EventId eventId,
    ClientId clientId,
    EventKey eventKey,
    String content,
    Instant occurredAt
) {}
