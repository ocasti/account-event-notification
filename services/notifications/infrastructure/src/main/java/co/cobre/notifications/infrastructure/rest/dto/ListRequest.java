package co.cobre.notifications.infrastructure.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.BindParam;

import java.time.Instant;
import java.util.Optional;

/**
 * Request DTO for listing notification events.
 */
public record ListRequest(
    Optional<Instant> from,
    Optional<Instant> to,
    @BindParam("delivery_status")
    Optional<String> deliveryStatus,
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
}
