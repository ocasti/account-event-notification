package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.application.port.out.WebhookSender;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryOutcome;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.domain.model.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Webhook sender implementation using RestClient.
 * Sends webhooks with event-timestamp, event-signature, x-cobre-event-id, and x-cobre-attempt headers.
 * Maps response codes: 2xx to Success, 5xx/408/429/timeout/connection errors to TransientFailure, other 4xx to PermanentFailure.
 */
@Component
public class HttpWebhookSender implements WebhookSender {
    private final RestClient webhookRestClient;
    private final WebhookSigner signer;
    private final WebhookPayloadMapper payloadMapper;
    private final WebhookUrlValidator urlValidator;

    /**
     * Creates a new HTTP webhook sender.
     */
    public HttpWebhookSender(
        RestClient webhookRestClient,
        WebhookSigner signer,
        WebhookPayloadMapper payloadMapper,
        WebhookUrlValidator urlValidator
    ) {
        this.webhookRestClient = webhookRestClient;
        this.signer = signer;
        this.payloadMapper = payloadMapper;
        this.urlValidator = urlValidator;
    }

    /**
     * Sends a notification via webhook to the subscription endpoint.
     */
    @Override
    public DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt) {
        throw new UnsupportedOperationException("not implemented");
    }
}
