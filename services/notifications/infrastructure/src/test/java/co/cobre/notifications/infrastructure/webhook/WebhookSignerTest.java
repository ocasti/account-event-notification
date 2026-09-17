package co.cobre.notifications.infrastructure.webhook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    static final Instant FIXED_TIME = Instant.parse("2025-09-15T10:30:45.123Z");
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

    @Test
    void shouldComputeHmacSignatureWhenBodyAndKeyProvided() throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);
        var signatureKey = "secret-key";
        var body = "{\"id\":\"test\"}";

        var signature = signer.sign(signatureKey, body);
        var expectedValue = computeHmacSha256(signatureKey, signature.timestamp() + "." + body);

        assertThat(signature.timestamp()).isEqualTo("2025-09-15T10:30:45.123Z");
        assertThat(signature.value()).isEqualTo(expectedValue);
    }

    @Test
    void shouldProduceLowercaseHexSignatureWhenSigningBody() throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);
        var signatureKey = "secret-key";
        var body = "test-body";

        var signature = signer.sign(signatureKey, body);

        assertThat(signature.value()).matches("[a-f0-9]+");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("distinctInputRows")
    void shouldProduceDifferentSignaturesWhenInputsDiffer(
        String rowName, String key1, String body1, String key2, String body2
    ) throws Exception {
        var signer = new WebhookSigner(FIXED_CLOCK);

        var signature1 = signer.sign(key1, body1);
        var signature2 = signer.sign(key2, body2);

        assertThat(signature1.value()).isNotEqualTo(signature2.value());
    }

    private static Stream<Arguments> distinctInputRows() {
        return Stream.of(
            Arguments.of("different keys, same body", "key1", "test-body", "key2", "test-body"),
            Arguments.of("same key, different bodies", "secret", "body1", "secret", "body2")
        );
    }

    private String computeHmacSha256(String key, String data) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        mac.init(new javax.crypto.spec.SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA256"));
        var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(hash).toLowerCase();
    }
}
