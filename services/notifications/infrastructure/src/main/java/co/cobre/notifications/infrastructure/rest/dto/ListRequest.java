package co.cobre.notifications.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Optional;

/**
 * Request DTO for listing notification events.
 */
public record ListRequest(
    Optional<Instant> from,
    Optional<Instant> to,
    @JsonProperty("delivery_status")
    Optional<String> delivery_status,
    @Min(1)
    @Max(100)
    Integer limit,
    Optional<String> cursor
) {
    public ListRequest {
        if (limit == null) {
            limit = 20;
        }
    }

    public Optional<String> deliveryStatus() {
        return delivery_status;
    }
}
