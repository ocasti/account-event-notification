package co.cobre.simulator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message contract for account events sent to SQS (snake_case JSON).
 */
public record AccountEventMessage(
    @JsonProperty("event_id")
    String eventId,
    @JsonProperty("event_type")
    String eventType,
    @JsonProperty("client_id")
    String clientId,
    String content,
    @JsonProperty("occurred_at")
    String occurredAt
) {

    static AccountEventMessage from(ReferenceEvent event) {
        return new AccountEventMessage(
            event.eventId(),
            event.eventType(),
            event.clientId(),
            event.content(),
            event.occurredAt().toString()
        );
    }
}
