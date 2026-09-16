package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a replay operation.
 */
public record ReplayResponse(
    @Schema(description = "Notification event identifier", example = "EVT003")
    @JsonProperty("event_id")
    String eventId,
    @Schema(description = "New delivery cycle scheduled by this replay", example = "2")
    @JsonProperty("cycle")
    int cycle,
    @Schema(description = "Delivery status of the new cycle right after it is scheduled", example = "pending")
    @JsonProperty("delivery_status")
    String deliveryStatus
) {}
