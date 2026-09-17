package co.cobre.notifications.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void shouldReturnStandardDefaultsWhenCreatingStandardPolicy() {
        var policy = RetryPolicy.standard();

        assertThat(policy.baseDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.factor()).isEqualTo(4.0);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.jitterRatio()).isEqualTo(0.2);
        assertThat(policy.maxAttempts()).isEqualTo(5);
    }

    @ParameterizedTest(name = "shouldComputeDelayWhenAttemptNumberIs{0}")
    @CsvSource({
        "1,0",
        "2,30",
        "3,120",
        "4,480",
        "5,900"
    })
    void shouldComputeDelayWhenAttemptNumberVaries(int attemptNumber, long expectedSeconds) {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        var delay = policy.delayBefore(attemptNumber, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @ParameterizedTest(name = "shouldApplyJitterWhenRandomValueIs{0}")
    @CsvSource({
        "0.0,24",
        "1.0,36"
    })
    void shouldApplyJitterWhenRandomValueVaries(double randomValue, long expectedSeconds) {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(randomValue);

        var delay = policy.delayBefore(2, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    void shouldNotExceedMaxDelayWhenJitterPushesAboveCap() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(1.0);

        var delay = policy.delayBefore(5, random);

        assertThat(delay).isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @ParameterizedTest(name = "shouldReportExhaustedWhenAttemptCountIs{0}")
    @CsvSource({
        "5,false",
        "6,true"
    })
    void shouldReportExhaustedWhenAttemptCountVaries(int attemptNumber, boolean expectedExhausted) {
        var policy = RetryPolicy.standard();

        var exhausted = policy.isExhausted(attemptNumber);

        assertThat(exhausted).isEqualTo(expectedExhausted);
    }

    @Test
    void shouldRejectDelayComputationWhenAttemptNumberIsZero() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        assertThatThrownBy(() -> policy.delayBefore(0, random))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private RandomGenerator fixedRandom(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
