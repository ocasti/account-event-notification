package co.cobre.notifications.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIdTest {

    @Test
    void shouldCreateClientIdWhenValueIsValid() {
        var clientId = new ClientId("client-123");

        assertThat(clientId.value()).isEqualTo("client-123");
    }

    @Test
    void shouldAcceptClientIdWhenValueIsAtMaxLength() {
        var value = "a".repeat(64);

        var clientId = new ClientId(value);

        assertThat(clientId.value().length()).isEqualTo(64);
    }

    @ParameterizedTest(name = "shouldRejectClientIdWhenValueIs{0}")
    @MethodSource("invalidClientIdValues")
    void shouldRejectClientIdWhenValueIsInvalid(String label, String value, String expectedMessageFragment) {
        assertThatThrownBy(() -> new ClientId(value))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(expectedMessageFragment);
    }

    private static Stream<Arguments> invalidClientIdValues() {
        return Stream.of(
            Arguments.of("Null", null, "cannot be null"),
            Arguments.of("Blank", "   ", "cannot be blank"),
            Arguments.of("LongerThan64Characters", "a".repeat(65), "cannot exceed 64")
        );
    }
}
