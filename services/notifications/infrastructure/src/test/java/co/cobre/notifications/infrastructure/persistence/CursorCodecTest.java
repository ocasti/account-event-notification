package co.cobre.notifications.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void shouldPreserveCreatedAtAndEventIdWhenEncodingThenDecoding() {
        var createdAt = Instant.parse("2024-01-01T00:00:00Z");

        var encoded = CursorCodec.encode(createdAt, "evt-1");
        var decoded = CursorCodec.decode(encoded);

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.eventId()).isEqualTo("evt-1");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenCursorHasNoSeparator() {
        var withoutSeparator = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("no-separator-here".getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(withoutSeparator))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid cursor format");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenSeparatorIsFirstCharacter() {
        var separatorAtStart = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("|evt-1".getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(separatorAtStart))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid cursor format");
    }
}
