package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventIdTest {

    @Test
    void shouldAcceptValidEventId() {
        var eventId = new EventId("event-123");

        assertThat(eventId.value()).isEqualTo("event-123");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new EventId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() -> new EventId("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be blank");
    }

    @Test
    void shouldRejectValueLongerThan64Characters() {
        var longValue = "a".repeat(65);
        assertThatThrownBy(() -> new EventId(longValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot exceed 64");
    }

    @Test
    void shouldAccept64CharacterValue() {
        var value = "a".repeat(64);
        var eventId = new EventId(value);

        assertThat(eventId.value().length()).isEqualTo(64);
    }
}
