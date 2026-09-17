package co.cobre.notifications.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryResultTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("outcomes")
    void shouldMapResultFieldsWhenOutcomeIsGiven(
        String caseName,
        DeliveryOutcome outcome,
        Optional<Integer> expectedStatus,
        Optional<String> expectedFailureReason,
        Optional<Duration> expectedLatency,
        boolean expectedRetryable) {

        var result = DeliveryResult.of(outcome);

        assertThat(result.responseStatus()).isEqualTo(expectedStatus);
        assertThat(result.failureReason()).isEqualTo(expectedFailureReason);
        assertThat(result.latency()).isEqualTo(expectedLatency);
        assertThat(outcome.isRetryable()).isEqualTo(expectedRetryable);
    }

    private static Stream<Arguments> outcomes() {
        return Stream.of(
            Arguments.of(
                "success",
                new DeliveryOutcome.Success(200, Duration.ofMillis(100)),
                Optional.of(200),
                Optional.empty(),
                Optional.of(Duration.ofMillis(100)),
                false),
            Arguments.of(
                "transient failure",
                new DeliveryOutcome.TransientFailure(Optional.of(503), "service unavailable", Duration.ofMillis(50)),
                Optional.of(503),
                Optional.of("service unavailable"),
                Optional.of(Duration.ofMillis(50)),
                true),
            Arguments.of(
                "permanent failure",
                new DeliveryOutcome.PermanentFailure(410, "gone", Duration.ofMillis(75)),
                Optional.of(410),
                Optional.of("gone"),
                Optional.of(Duration.ofMillis(75)),
                false));
    }
}
