package co.cobre.notifications.domain;

import java.time.Instant;
import java.util.Objects;

public record EventData(
    EventId eventId,
    ClientId clientId,
    EventKey eventKey,
    String content,
    Instant occurredAt
) {
    public EventData {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(eventKey);
        Objects.requireNonNull(content);
        Objects.requireNonNull(occurredAt);
    }
}
