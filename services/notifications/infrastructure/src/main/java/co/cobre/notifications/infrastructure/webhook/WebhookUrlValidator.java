package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.WebhookUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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

    @Autowired
    public WebhookUrlValidator(WebhookProperties props) {
        this(props, host -> {
            try {
                return List.of(InetAddress.getAllByName(host));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Webhook host does not resolve: " + host, e);
            }
        });
    }

    WebhookUrlValidator(WebhookProperties props, Function<String, List<InetAddress>> resolver) {
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

        if (props.requireHttps() && "http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("HTTPS is required for webhook URLs");
        }

        if ("http".equalsIgnoreCase(scheme) && !props.allowlist().contains(host)) {
            throw new IllegalArgumentException("HTTP scheme requires host to be in allowlist");
        }

        var addresses = resolveHost(host);
        validateAddress(host, addresses);
        return addresses;
    }

    /**
     * Validates a hostname and returns the resolved InetAddress.
     * Used by DNS resolver to ensure pinning after validation.
     */
    public InetAddress validateHost(String host) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host must not be null or empty");
        }

        var address = resolveHost(host);
        validateAddress(host, address);
        return address;
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

        if (isUniqueLocalAddress(address)) {
            throw new IllegalArgumentException("Webhook URL resolves to a unique local address");
        }
    }

    private boolean isUniqueLocalAddress(InetAddress address) {
        if (address instanceof Inet6Address v6) {
            var bytes = v6.getAddress();
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
