package co.cobre.notifications.domain;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Value object representing a webhook URL (HTTP or HTTPS).
 * HTTPS is enforced by policy in WebhookUrlValidator, not in this domain object.
 */
public record WebhookUrl(URI value) {

    public WebhookUrl {
        if (value == null) {
            throw new IllegalArgumentException("Webhook URL cannot be null");
        }
        if (!isValidWebhookUrl(value)) {
            throw new IllegalArgumentException(
                "Webhook URL must be HTTP or HTTPS with a non-empty host and no fragment"
            );
        }
    }

    /**
     * Creates a WebhookUrl from a raw string.
     */
    public static WebhookUrl of(String raw) {
        try {
            URI uri = new URI(raw);
            return new WebhookUrl(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + raw, e);
        }
    }

    private static boolean isValidWebhookUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        return uri.getFragment() == null;
    }
}
