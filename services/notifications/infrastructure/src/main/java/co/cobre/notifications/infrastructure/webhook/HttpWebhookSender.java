package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Webhook sender implementation using RestClient.
 * Sends webhooks with event-timestamp, event-signature, x-cobre-event-id, and x-cobre-attempt headers.
 * Maps response codes: 2xx to Success, 5xx/408/429/timeout/connection errors to TransientFailure, other 4xx to PermanentFailure.
 */
@Component
public class HttpWebhookSender implements WebhookSender {
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;
    private static final int HTTP_CLIENT_ERROR_MIN = 400;
    private static final int HTTP_CLIENT_ERROR_MAX_EXCLUSIVE = 500;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final int HTTP_SERVER_ERROR_MAX_EXCLUSIVE = 600;
    private static final int HTTP_REQUEST_TIMEOUT = 408;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

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
        } catch (IllegalArgumentException e) {
            return new DeliveryOutcome.PermanentFailure(0, "invalid webhook url", Duration.ZERO);
        }

        var startTime = System.nanoTime();
        var json = payloadMapper.toJson(event);

        try {
            var response = buildRequest(subscription, event, attempt, json)
                .body(json)
                .exchange((req, res) -> res);

            var latency = Duration.ofNanos(System.nanoTime() - startTime);
            return classify(response.getStatusCode().value(), latency);
        } catch (ResourceAccessException | IOException e) {
            var latency = Duration.ofNanos(System.nanoTime() - startTime);
            return new DeliveryOutcome.TransientFailure(Optional.empty(), e.getMessage(), latency);
        }
    }

    private RestClient.RequestBodySpec buildRequest(
        Subscription subscription, NotificationEvent event, DeliveryAttempt attempt, String json
    ) {
        var requestBuilder = webhookRestClient.post()
            .uri(subscription.url().value())
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-cobre-event-id", event.eventId().value())
            .header("x-cobre-attempt", String.valueOf(attempt.attemptNumber()));

        subscription.signatureKey().ifPresent(key -> {
            var sig = signer.sign(key, json);
            requestBuilder.header("event-timestamp", sig.timestamp());
            requestBuilder.header("event-signature", sig.value());
        });
        return requestBuilder;
    }

    private DeliveryOutcome classify(int status, Duration latency) {
        if (isSuccess(status)) {
            return new DeliveryOutcome.Success(status, latency);
        }
        if (isTransient(status)) {
            return new DeliveryOutcome.TransientFailure(Optional.of(status), "HTTP " + status, latency);
        }
        if (isClientError(status)) {
            return new DeliveryOutcome.PermanentFailure(status, "client rejected: " + status, latency);
        }
        return new DeliveryOutcome.TransientFailure(Optional.of(status), "unexpected: " + status, latency);
    }

    private boolean isSuccess(int status) {
        return status >= HTTP_SUCCESS_MIN && status < HTTP_SUCCESS_MAX_EXCLUSIVE;
    }

    private boolean isTransient(int status) {
        return (status >= HTTP_SERVER_ERROR_MIN && status < HTTP_SERVER_ERROR_MAX_EXCLUSIVE)
            || status == HTTP_REQUEST_TIMEOUT
            || status == HTTP_TOO_MANY_REQUESTS;
    }

    private boolean isClientError(int status) {
        return status >= HTTP_CLIENT_ERROR_MIN && status < HTTP_CLIENT_ERROR_MAX_EXCLUSIVE;
    }
}
