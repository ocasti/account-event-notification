package co.cobre.simulator;

import java.time.Instant;

/**
 * A reference event from the catalog, loaded from the events JSON.
 */
public record ReferenceEvent(
    String eventId,
    String eventType,
    String clientId,
    String content,
    Instant occurredAt
) {
}
