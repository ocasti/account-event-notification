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
        try {
            var mac = Mac.getInstance("HmacSHA256");
            var keyBytes = key.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA256"));
            var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
