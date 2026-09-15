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
        try {
            var iso8601Time = event.createdAt().toString();
            var json = "{" +
                "\"id\":\"" + event.eventId().value() + "\"," +
                "\"event_key\":\"" + event.eventKey().value() + "\"," +
                "\"client_id\":\"" + event.clientId().value() + "\"," +
                "\"created_at\":\"" + iso8601Time + "\"," +
                "\"content\":" + jsonMapper.writeValueAsString(event.content()) +
                "}";
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize webhook payload", e);
        }
    }
}
