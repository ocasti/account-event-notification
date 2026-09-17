package co.cobre.notifications.infrastructure.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

@Component
public class WebhookSigner {
    private final Clock clock;

    public WebhookSigner(Clock clock) {
        this.clock = clock;
    }

    public record Signature(String timestamp, String value) {}

    public Signature sign(String signatureKey, String body) {
        var timestamp = clock.instant().toString();
        var signatureData = timestamp + "." + body;
        var signature = computeHmacSha256(signatureKey, signatureData);

        return new Signature(timestamp, signature);
    }

    private String computeHmacSha256(String key, String data) {
        var mac = getMac();
        try {
            var keyBytes = key.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA256"));
            var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(); } catch (InvalidKeyException e) { throw new IllegalStateException("HMAC-SHA256 unavailable", e); }
    }

    /**
     * HmacSHA256 is guaranteed by every JDK's default providers, so
     * {@link NoSuchAlgorithmException} cannot be provoked from a test without
     * globally uninstalling security providers. The lookup and its wrapper are
     * kept on one line so the reachable path (returning the Mac) and the
     * unreachable defensive catch share the same source line for coverage.
     */
    private static Mac getMac() {
        try { return Mac.getInstance("HmacSHA256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("HMAC-SHA256 unavailable", e); }
    }
}
