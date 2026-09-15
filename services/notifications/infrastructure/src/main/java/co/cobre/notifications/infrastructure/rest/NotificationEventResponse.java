package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Optional;

/**
 * Response DTO for a notification event summary.
 */
public record NotificationEventResponse(
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
    int attemptsCount
) {}
