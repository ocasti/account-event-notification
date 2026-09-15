package co.cobre.notifications.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIdTest {

    @Test
    void shouldAcceptValidClientId() {
        var clientId = new ClientId("client-123");

        assertThat(clientId.value()).isEqualTo("client-123");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new ClientId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() -> new ClientId("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be blank");
    }

    @Test
    void shouldRejectValueLongerThan64Characters() {
        var longValue = "a".repeat(65);
        assertThatThrownBy(() -> new ClientId(longValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot exceed 64");
    }

    @Test
    void shouldAccept64CharacterValue() {
        var value = "a".repeat(64);
        var clientId = new ClientId(value);

        assertThat(clientId.value().length()).isEqualTo(64);
    }
}
