package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.NotificationEvent;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting domain objects to webhook payloads.
 */
@Component
public class WebhookPayloadMapper {

    /**
     * Maps a notification event to a webhook payload.
     */
    public WebhookPayload toPayload(NotificationEvent event) {
        throw new UnsupportedOperationException("not implemented");
    }
}
