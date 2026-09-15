package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.NotificationEvent;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting domain objects to webhook payloads.
 */
@Component
public class WebhookPayloadMapper {
    private final JsonMapper jsonMapper;

    public WebhookPayloadMapper(JsonMapper jsonMapper) {
        var mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
        this.jsonMapper = mapper;
    }

    public String toJson(NotificationEvent event) {
        try {
            var payload = new WebhookPayload(
                event.eventId().value(),
                event.eventKey().value(),
                event.clientId().value(),
                event.createdAt(),
                event.content()
            );
            return jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize webhook payload", e);
        }
    }
}
