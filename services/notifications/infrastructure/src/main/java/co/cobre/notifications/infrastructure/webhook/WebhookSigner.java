package co.cobre.notifications.infrastructure.webhook;

import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Signs webhook payloads using HMAC-SHA256.
 */
@Component
public class WebhookSigner {
    private final Clock clock;

    public WebhookSigner(Clock clock) {
        this.clock = clock;
    }

    public record Signature(String timestamp, String value) {}

    /**
     * Signs a webhook payload using HMAC-SHA256.
     * Produces hex-encoded signature of timestamp + "." + body in UTF-8.
     */
    public Signature sign(String signatureKey, String body) {
        throw new UnsupportedOperationException("not implemented");
    }
}
