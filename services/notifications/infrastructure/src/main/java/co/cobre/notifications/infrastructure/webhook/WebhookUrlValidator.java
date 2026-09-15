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

    public WebhookUrlValidator(WebhookProperties props) {
        this(props, host -> {
            try {
                return List.of(InetAddress.getAllByName(host));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public WebhookUrlValidator(WebhookProperties props, Function<String, List<InetAddress>> resolver) {
        this.props = props;
        this.resolver = resolver;
    }

    public InetAddress validate(WebhookUrl url) {
        return validate(url.value());
    }

    public InetAddress validate(URI uri) {
        var scheme = uri.getScheme();
        var host = uri.getHost();

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Webhook URL must have a valid host");
        }

        if (!props.requireHttps() && "http".equalsIgnoreCase(scheme)) {
            if (props.allowlist().contains(host)) {
                return resolveHost(host);
            }
            throw new IllegalArgumentException("HTTP scheme requires host to be in allowlist");
        }

        var addresses = resolveHost(host);
        validateAddress(host, addresses);
        return addresses;
    }

    private InetAddress resolveHost(String host) {
        var addresses = resolver.apply(host);
        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalArgumentException("Unable to resolve host: " + host);
        }
        return addresses.getFirst();
    }

    private void validateAddress(String host, InetAddress address) {
        if (props.allowlist().contains(host)) {
            return;
        }

        if (address.isSiteLocalAddress() ||
            address.isLoopbackAddress() ||
            address.isLinkLocalAddress() ||
            address.isAnyLocalAddress() ||
            address.isMulticastAddress()) {
            throw new IllegalArgumentException("Webhook URL resolves to a restricted address");
        }

        var hostAddress = address.getHostAddress();
        if (isUniqueLocalAddress(hostAddress)) {
            throw new IllegalArgumentException("Webhook URL resolves to a unique local address");
        }
    }

    private boolean isUniqueLocalAddress(String hostAddress) {
        if (hostAddress.startsWith("fd") || hostAddress.startsWith("fc")) {
            try {
                var bytes = InetAddress.getByName(hostAddress).getAddress();
                if (bytes.length == 16) {
                    return (bytes[0] & 0xFE) == 0xFC;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }
}
