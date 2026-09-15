package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.WebhookUrl;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.function.Function;

/**
 * Validates webhook URLs for security constraints.
 * Rejects non-HTTPS unless allowlisted, resolves DNS and rejects private IP ranges,
 * loopback and link-local addresses unless allowlisted.
 */
@Component
public class WebhookUrlValidator {
    private final WebhookProperties props;
    private final Function<String, List<InetAddress>> resolver;

    /**
     * Creates a new webhook URL validator.
     */
    public WebhookUrlValidator(WebhookProperties props) {
        this(props, host -> {
            try {
                return List.of(InetAddress.getAllByName(host));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Creates a new webhook URL validator with custom DNS resolver.
     */
    public WebhookUrlValidator(WebhookProperties props, Function<String, List<InetAddress>> resolver) {
        this.props = props;
        this.resolver = resolver;
    }

    /**
     * Validates a webhook URL for security constraints.
     * Returns the validated IP address.
     */
    public InetAddress validate(WebhookUrl url) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Validates a URI for security constraints.
     * Used internally by DNS resolver.
     */
    public InetAddress validate(URI uri) {
        throw new UnsupportedOperationException("not implemented");
    }
}
