package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ListRequestTest {

    @ParameterizedTest(name = "should parse status param \"{0}\" as {1}")
    @MethodSource("validStatusParamCases")
    void shouldParseStatusWhenStatusParamIsValidOrAbsent(Optional<String> statusParam, Optional<DeliveryStatus> expectedStatus) {
        var request = new ListRequest(Optional.empty(), Optional.empty(), statusParam, 20, Optional.empty());

        var result = request.status();

        assertThat(result).isEqualTo(expectedStatus);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenStatusParamIsBogus() {
        var request = new ListRequest(
            Optional.empty(),
            Optional.empty(),
            Optional.of("bogus"),
            20,
            Optional.empty()
        );

        var thrown = catchThrowable(request::status);

        assertThat(thrown)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid delivery status: bogus");
    }

    private static Stream<Arguments> validStatusParamCases() {
        return Stream.of(
            Arguments.of(Optional.of("failed"), Optional.of(DeliveryStatus.FAILED)),
            Arguments.of(Optional.<String>empty(), Optional.<DeliveryStatus>empty()),
            Arguments.of(Optional.of("COMPLETED"), Optional.of(DeliveryStatus.COMPLETED))
        );
    }
}
