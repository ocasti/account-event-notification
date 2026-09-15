package co.cobre.notifications.infrastructure.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Webhook payload DTO with Jackson snake_case serialization.
 */
public record WebhookPayload(
    @JsonProperty("id")
    String id,
    @JsonProperty("event_key")
    String eventKey,
    @JsonProperty("client_id")
    String clientId,
    @JsonProperty("created_at")
    Instant createdAt,
    @JsonProperty("content")
    String content
) {}
