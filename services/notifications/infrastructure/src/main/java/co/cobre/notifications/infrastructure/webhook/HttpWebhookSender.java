package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.application.port.out.WebhookSender;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryOutcome;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.domain.model.Subscription;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

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

    @Override
    public DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt) {
        try {
            urlValidator.validate(subscription.url());
        } catch (Exception e) {
            return new DeliveryOutcome.PermanentFailure(0, "invalid webhook url", Duration.ZERO);
        }

        var startTime = System.nanoTime();
        var json = payloadMapper.toJson(event);

        try {
            var requestBuilder = webhookRestClient.post()
                .uri(subscription.url().value())
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-cobre-event-id", event.eventId().value())
                .header("x-cobre-attempt", String.valueOf(attempt.attemptNumber()));

            if (subscription.signatureKey().isPresent()) {
                var sig = signer.sign(subscription.signatureKey().get(), json);
                requestBuilder.header("event-timestamp", sig.timestamp());
                requestBuilder.header("event-signature", sig.value());
            } else {
                requestBuilder.header("event-timestamp", event.createdAt().toString());
            }

            var response = requestBuilder
                .body(json)
                .exchange((req, res) -> res);

            var latency = Duration.ofNanos(System.nanoTime() - startTime);
            var status = response.getStatusCode().value();

            if (status >= 200 && status < 300) {
                return new DeliveryOutcome.Success(status, latency);
            } else if ((status >= 500 && status < 600) || status == 408 || status == 429) {
                return new DeliveryOutcome.TransientFailure(Optional.of(status), "HTTP " + status, latency);
            } else if (status >= 400 && status < 500) {
                return new DeliveryOutcome.PermanentFailure(status, "client rejected: " + status, latency);
            } else {
                return new DeliveryOutcome.TransientFailure(Optional.of(status), "unexpected: " + status, latency);
            }
        } catch (ResourceAccessException e) {
            var latency = Duration.ofNanos(System.nanoTime() - startTime);
            return new DeliveryOutcome.TransientFailure(Optional.empty(), e.getMessage(), latency);
        }
    }
}
