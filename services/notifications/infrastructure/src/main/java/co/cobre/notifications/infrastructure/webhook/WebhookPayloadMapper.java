package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.NotificationEvent;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting domain objects to webhook payloads.
 */
@Component
public class WebhookPayloadMapper {
    private final JsonMapper jsonMapper;

    public WebhookPayloadMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Maps a notification event to a JSON string.
     */
    public String toJson(NotificationEvent event) {
        throw new UnsupportedOperationException("not implemented");
    }
}
