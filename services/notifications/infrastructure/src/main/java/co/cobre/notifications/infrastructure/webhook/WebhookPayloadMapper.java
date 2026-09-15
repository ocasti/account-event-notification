package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.NotificationEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mapper for converting domain objects to webhook payloads.
 */
@Component
public class WebhookPayloadMapper {
    private final JsonMapper jsonMapper;

    public WebhookPayloadMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String toJson(NotificationEvent event) {
        var payload = new WebhookPayload(
            event.eventId().value(),
            event.eventKey().value(),
            event.clientId().value(),
            event.createdAt(),
            event.content()
        );
        return jsonMapper.writeValueAsString(payload);
    }
}
