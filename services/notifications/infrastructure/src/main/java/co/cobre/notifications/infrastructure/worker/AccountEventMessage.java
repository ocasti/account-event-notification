package co.cobre.notifications.infrastructure.worker;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AccountEventMessage(
    @JsonProperty("event_id")
    String eventId,

    @JsonProperty("event_type")
    String eventType,

    @JsonProperty("client_id")
    String clientId,

    @JsonProperty("content")
    String content,

    @JsonProperty("occurred_at")
    Instant occurredAt
) {
}
