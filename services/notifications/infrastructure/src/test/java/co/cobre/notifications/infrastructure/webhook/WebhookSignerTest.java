package co.cobre.notifications.infrastructure.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    static final Instant FIXED_TIME = Instant.parse("2025-09-15T10:30:45.123000000Z");
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

    @Test
    void shouldSignWithTimestampAndBodyInISO8601() throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);
        var signatureKey = "secret-key";
        var body = "{\"id\":\"test\"}";

        var signature = signer.sign(signatureKey, body);

        assertThat(signature.timestamp()).isEqualTo("2025-09-15T10:30:45.123000000Z");

        var expectedValue = computeHmacSha256(signatureKey, signature.timestamp() + "." + body);
        assertThat(signature.value()).isEqualTo(expectedValue);
    }

    @Test
    void shouldProduceHexEncodedSignatureInLowercase() throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);
        var signatureKey = "secret-key";
        var body = "test-body";

        var signature = signer.sign(signatureKey, body);

        assertThat(signature.value()).matches("[a-f0-9]+");
    }

    @Test
    void differentKeysProduceDifferentSignatures() throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);
        var body = "test-body";

        var signature1 = signer.sign("key1", body);
        var signature2 = signer.sign("key2", body);

        assertThat(signature1.value()).isNotEqualTo(signature2.value());
    }

    @Test
    void shouldIncludeTimestampAndBodyInSignature() throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);
        var signatureKey = "secret";
        var body1 = "body1";
        var body2 = "body2";

        var signature1 = signer.sign(signatureKey, body1);
        var signature2 = signer.sign(signatureKey, body2);

        assertThat(signature1.value()).isNotEqualTo(signature2.value());
    }

    private String computeHmacSha256(String key, String data) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        mac.init(new javax.crypto.spec.SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA256"));
        var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(hash).toLowerCase();
    }
}
