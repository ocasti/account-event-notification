package co.cobre.notifications.infrastructure.webhook;

import org.springframework.stereotype.Component;

/**
 * Signs webhook payloads using HMAC-SHA256.
 */
@Component
public class WebhookSigner {

    /**
     * Signs a webhook payload using HMAC-SHA256.
     * Produces hex-encoded signature of timestamp + "." + body in UTF-8.
     */
    public String sign(String signatureKey, String timestamp, String body) {
        throw new UnsupportedOperationException("not implemented");
    }
}
