package co.cobre.notifications.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Response DTO for a notification event with full details and delivery attempts.
 */
public record NotificationEventDetailResponse(
    @JsonProperty("event_id")
    String eventId,
    @JsonProperty("event_type")
    String eventType,
    @JsonProperty("client_id")
    String clientId,
    @JsonProperty("content")
    String content,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("delivery_status")
    String deliveryStatus,
    @JsonProperty("delivery_date")
    Optional<Instant> deliveryDate,
    @JsonProperty("attempts_count")
    int attemptsCount,
    @JsonProperty("attempts")
    List<DeliveryAttemptResponse> attempts,
    @JsonProperty("next_attempt_at")
    Optional<Instant> nextAttemptAt
) {}
