package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventKeyTest {

    @Test
    void shouldAcceptSimpleKey() {
        var key = new EventKey("user_created");

        assertThat(key.value()).isEqualTo("user_created");
        assertThat(key.isWildcard()).isFalse();
    }

    @Test
    void shouldAcceptDotNotationKey() {
        var key = new EventKey("user.account.created");

        assertThat(key.value()).isEqualTo("user.account.created");
        assertThat(key.isWildcard()).isFalse();
    }

    @Test
    void shouldAcceptWildcard() {
        var key = new EventKey("*");

        assertThat(key.value()).isEqualTo("*");
        assertThat(key.isWildcard()).isTrue();
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new EventKey(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectInvalidPattern() {
        assertThatThrownBy(() -> new EventKey("User.Created"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be either");
    }

    @Test
    void shouldRejectMixedCaseInKey() {
        assertThatThrownBy(() -> new EventKey("user.Account.created"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectKeyStartingWithDot() {
        assertThatThrownBy(() -> new EventKey(".user.created"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectKeyWithConsecutiveDots() {
        assertThatThrownBy(() -> new EventKey("user..created"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectKeyWithSpecialCharacters() {
        assertThatThrownBy(() -> new EventKey("user@created"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptNumbersInKey() {
        var key = new EventKey("order123.item_456.created");

        assertThat(key.value()).isEqualTo("order123.item_456.created");
    }

    @Test
    void shouldProvideWildcardFactory() {
        var key = EventKey.wildcard();

        assertThat(key.value()).isEqualTo("*");
        assertThat(key.isWildcard()).isTrue();
    }
}
