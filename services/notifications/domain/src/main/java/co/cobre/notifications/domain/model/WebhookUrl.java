package co.cobre.notifications.domain.model;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Value object representing a webhook URL with HTTPS validation.
 */
public record WebhookUrl(URI value) {

    public WebhookUrl {
        if (value == null) {
            throw new IllegalArgumentException("Webhook URL cannot be null");
        }
        if (!isValidHttpsUrl(value)) {
            throw new IllegalArgumentException(
                "Webhook URL must be a valid HTTPS URL with a non-empty host and no fragment"
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

    private static boolean isValidHttpsUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        return uri.getFragment() == null;
    }
}
