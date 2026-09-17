package co.cobre.notifications.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @ParameterizedTest(name = "shouldAllowTransitionWhenMovingFrom{0}To{1}")
    @MethodSource("validTransitions")
    void shouldAllowTransitionWhenTransitionIsValid(DeliveryStatus from, DeliveryStatus to) {
        var allowed = from.canTransitionTo(to);

        assertThat(allowed).isTrue();
    }

    private static Stream<Arguments> validTransitions() {
        return Stream.of(
            Arguments.of(DeliveryStatus.PENDING, DeliveryStatus.COMPLETED),
            Arguments.of(DeliveryStatus.PENDING, DeliveryStatus.RETRYING),
            Arguments.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED),
            Arguments.of(DeliveryStatus.RETRYING, DeliveryStatus.COMPLETED),
            Arguments.of(DeliveryStatus.RETRYING, DeliveryStatus.RETRYING),
            Arguments.of(DeliveryStatus.RETRYING, DeliveryStatus.FAILED),
            Arguments.of(DeliveryStatus.FAILED, DeliveryStatus.PENDING)
        );
    }

    @ParameterizedTest(name = "shouldRejectTransitionWhenMovingFrom{0}To{1}")
    @MethodSource("invalidTransitions")
    void shouldRejectTransitionWhenTransitionIsInvalid(DeliveryStatus from, DeliveryStatus to) {
        var allowed = from.canTransitionTo(to);

        assertThat(allowed).isFalse();
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
            Arguments.of(DeliveryStatus.COMPLETED, DeliveryStatus.PENDING),
            Arguments.of(DeliveryStatus.COMPLETED, DeliveryStatus.RETRYING),
            Arguments.of(DeliveryStatus.COMPLETED, DeliveryStatus.FAILED),
            Arguments.of(DeliveryStatus.COMPLETED, DeliveryStatus.SKIPPED),
            Arguments.of(DeliveryStatus.SKIPPED, DeliveryStatus.PENDING),
            Arguments.of(DeliveryStatus.SKIPPED, DeliveryStatus.RETRYING),
            Arguments.of(DeliveryStatus.SKIPPED, DeliveryStatus.COMPLETED),
            Arguments.of(DeliveryStatus.SKIPPED, DeliveryStatus.FAILED),
            Arguments.of(DeliveryStatus.PENDING, DeliveryStatus.PENDING),
            Arguments.of(DeliveryStatus.RETRYING, DeliveryStatus.PENDING),
            Arguments.of(DeliveryStatus.FAILED, DeliveryStatus.COMPLETED),
            Arguments.of(DeliveryStatus.FAILED, DeliveryStatus.RETRYING)
        );
    }

    @ParameterizedTest(name = "shouldReportTerminalWhenStatusIs{0}")
    @CsvSource({
        "PENDING,false",
        "RETRYING,false",
        "FAILED,false",
        "COMPLETED,true",
        "SKIPPED,true"
    })
    void shouldReportTerminalWhenStatusVaries(DeliveryStatus status, boolean expectedTerminal) {
        var terminal = status.isTerminal();

        assertThat(terminal).isEqualTo(expectedTerminal);
    }

    @ParameterizedTest(name = "shouldMapApiValueWhenValueIs{0}")
    @CsvSource({
        "FAILED,FAILED",
        "completed,COMPLETED",
        "bogus,"
    })
    void shouldMapApiValueWhenValueIsKnownOrUnknown(String apiValue, String expectedName) {
        var result = DeliveryStatus.fromApiValue(apiValue);

        assertThat(result.map(Enum::name)).isEqualTo(Optional.ofNullable(expectedName));
    }
}
