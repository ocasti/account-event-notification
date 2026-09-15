package co.cobre.simulator;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * Endpoints for manual event emission.
 */
@RestController
@RequestMapping("/simulator/events")
public class SimulatorController {

    private final EventGenerator generator;
    private final SqsEventPublisher publisher;
    private final Clock clock;

    public SimulatorController(EventGenerator generator, SqsEventPublisher publisher, Clock clock) {
        this.generator = generator;
        this.publisher = publisher;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<EmitResponse> emit(@Valid @RequestBody EmitRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    public record EmitRequest(
        @NotBlank
        @JsonProperty("client_id")
        String clientId,
        @NotBlank
        @JsonProperty("event_type")
        String eventType,
        @NotBlank
        String content
    ) {
    }

    public record EmitResponse(
        @JsonProperty("event_id")
        String eventId
    ) {
    }
}
