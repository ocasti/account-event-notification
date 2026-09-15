package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for a replay operation.
 */
public record ReplayResponse(
    @JsonProperty("event_id")
    String eventId,
    @JsonProperty("cycle")
    int cycle,
    @JsonProperty("delivery_status")
    String deliveryStatus
) {}
