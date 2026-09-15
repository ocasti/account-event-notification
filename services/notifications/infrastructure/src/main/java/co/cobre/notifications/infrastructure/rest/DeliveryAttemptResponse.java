package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Response DTO for a delivery attempt.
 */
public record DeliveryAttemptResponse(
    @JsonProperty("cycle")
    int cycle,
    @JsonProperty("attempt_number")
    int attemptNumber,
    @JsonProperty("executed_at")
    Optional<Instant> executedAt,
    @JsonProperty("response_status")
    Optional<Integer> responseStatus,
    @JsonProperty("failure_reason")
    Optional<String> failureReason,
    @JsonProperty("latency_ms")
    Optional<Long> latencyMs,
    @JsonProperty("origin")
    String origin
) {}
