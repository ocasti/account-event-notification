package co.cobre.notifications.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventKeyTest {

    @ParameterizedTest(name = "shouldAcceptEventKeyWhenValueIs{0}")
    @CsvSource({
        "user_created,false",
        "user.account.created,false",
        "*,true",
        "order123.item_456.created,false"
    })
    void shouldAcceptEventKeyWhenValueMatchesPattern(String value, boolean expectedWildcard) {
        var key = new EventKey(value);

        assertThat(key.value()).isEqualTo(value);
        assertThat(key.isWildcard()).isEqualTo(expectedWildcard);
    }

    @Test
    void shouldCreateWildcardEventKeyWhenUsingFactoryMethod() {
        var key = EventKey.wildcard();

        assertThat(key.value()).isEqualTo("*");
        assertThat(key.isWildcard()).isTrue();
    }

    @ParameterizedTest(name = "shouldRejectEventKeyWhenValueIs{0}")
    @MethodSource("invalidEventKeyValues")
    void shouldRejectEventKeyWhenValueIsInvalid(String label, String value, String expectedMessageFragment) {
        assertThatThrownBy(() -> new EventKey(value))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(expectedMessageFragment);
    }

    private static Stream<Arguments> invalidEventKeyValues() {
        return Stream.of(
            Arguments.of("Null", null, "cannot be null"),
            Arguments.of("MixedCaseSegment", "User.Created", "must be either"),
            Arguments.of("MixedCaseInMiddleSegment", "user.Account.created", "must be either"),
            Arguments.of("StartingWithDot", ".user.created", "must be either"),
            Arguments.of("ContainingConsecutiveDots", "user..created", "must be either"),
            Arguments.of("ContainingSpecialCharacters", "user@created", "must be either")
        );
    }
}
