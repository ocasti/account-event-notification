package co.cobre.simulator;

import java.time.Instant;

public record ReferenceEvent(
    String eventId,
    String eventType,
    String clientId,
    String content,
    Instant occurredAt
) {
}
