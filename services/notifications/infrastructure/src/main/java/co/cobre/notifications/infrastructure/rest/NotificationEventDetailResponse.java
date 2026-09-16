package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record NotificationEventDetailResponse(
    @Schema(description = "Notification event identifier", example = "EVT003")
    @JsonProperty("event_id")
    String eventId,
    @Schema(description = "Type of the account event that triggered the notification", example = "credit_transfer")
    @JsonProperty("event_type")
    String eventType,
    @Schema(description = "Identifier of the client that owns the event", example = "CLIENT002")
    @JsonProperty("client_id")
    String clientId,
    @Schema(description = "Human-readable description of the event", example = "Bank transfer received from Account #4567 for $1,500.00")
    @JsonProperty("content")
    String content,
    @Schema(description = "When the event was registered", example = "2024-03-15T11:20:18Z")
    @JsonProperty("created_at")
    Instant createdAt,
    @Schema(description = "Current delivery status: pending, retrying, completed or failed", example = "failed")
    @JsonProperty("delivery_status")
    String deliveryStatus,
    @Schema(description = "When the event was successfully delivered, if it ever was")
    @JsonProperty("delivery_date")
    Optional<Instant> deliveryDate,
    @Schema(description = "Number of delivery attempts made so far", example = "3")
    @JsonProperty("attempts_count")
    int attemptsCount,
    @Schema(description = "Full delivery attempt history, oldest first")
    @JsonProperty("attempts")
    List<DeliveryAttemptResponse> attempts,
    @Schema(description = "When the next delivery attempt is scheduled, if any is pending")
    @JsonProperty("next_attempt_at")
    Optional<Instant> nextAttemptAt
) {}
