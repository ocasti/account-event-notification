package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.WebhookUrl;
import org.springframework.stereotype.Component;

/**
 * Validates webhook URLs for security constraints.
 * Rejects non-HTTPS unless allowlisted, resolves DNS and rejects private IP ranges,
 * loopback and link-local addresses unless allowlisted.
 */
@Component
public class WebhookUrlValidator {
    private final WebhookProperties props;

    /**
     * Creates a new webhook URL validator.
     */
    public WebhookUrlValidator(WebhookProperties props) {
        this.props = props;
    }

    /**
     * Validates a webhook URL for security constraints.
     */
    public void validate(WebhookUrl url) {
        throw new UnsupportedOperationException("not implemented");
    }
}
