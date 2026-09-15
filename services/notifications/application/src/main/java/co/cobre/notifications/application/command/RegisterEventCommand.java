package co.cobre.notifications.application.command;

import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;

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
