package co.cobre.notifications.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * SQS account event message.
 */
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
