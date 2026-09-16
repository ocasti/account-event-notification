package co.cobre.notifications.infrastructure.webhook;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;

/**
 * DNS resolver that pins validated IPs from WebhookUrlValidator.
 * Prevents DNS rebinding attacks by using pre-validated addresses.
 */
public class PinnedDnsResolver implements DnsResolver {
    private final WebhookUrlValidator validator;

    public PinnedDnsResolver(WebhookUrlValidator validator) {
        this.validator = validator;
    }

    @Override
    public InetAddress[] resolve(String host) {
        var address = validator.validateHost(host);
        return new InetAddress[]{address};
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }
}
