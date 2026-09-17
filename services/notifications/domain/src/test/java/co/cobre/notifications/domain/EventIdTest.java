package co.cobre.notifications.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventIdTest {

    @Test
    void shouldCreateEventIdWhenValueIsValid() {
        var eventId = new EventId("event-123");

        assertThat(eventId.value()).isEqualTo("event-123");
    }

    @Test
    void shouldAcceptEventIdWhenValueIsAtMaxLength() {
        var value = "a".repeat(64);

        var eventId = new EventId(value);

        assertThat(eventId.value().length()).isEqualTo(64);
    }

    @ParameterizedTest(name = "shouldRejectEventIdWhenValueIs{0}")
    @MethodSource("invalidEventIdValues")
    void shouldRejectEventIdWhenValueIsInvalid(String label, String value, String expectedMessageFragment) {
        assertThatThrownBy(() -> new EventId(value))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(expectedMessageFragment);
    }

    private static Stream<Arguments> invalidEventIdValues() {
        return Stream.of(
            Arguments.of("Null", null, "cannot be null"),
            Arguments.of("Blank", "   ", "cannot be blank"),
            Arguments.of("LongerThan64Characters", "a".repeat(65), "cannot exceed 64")
        );
    }
}
