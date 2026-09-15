package co.cobre.notifications.domain.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void shouldReturnStandardPolicyWithCorrectDefaults() {
        var policy = RetryPolicy.standard();

        assertThat(policy.baseDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.factor()).isEqualTo(4.0);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.jitterRatio()).isEqualTo(0.2);
        assertThat(policy.maxAttempts()).isEqualTo(5);
    }

    @Test
    void shouldReturnZeroDelayBeforeFirstAttempt() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        var delay = policy.delayBefore(1, random);

        assertThat(delay).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldReturnBaseDelayBeforeSecondAttempt() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        var delay = policy.delayBefore(2, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldReturnTwoMinutesBeforeThirdAttempt() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        var delay = policy.delayBefore(3, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void shouldReturnEightMinutesBeforeFourthAttempt() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        var delay = policy.delayBefore(4, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(480));
    }

    @Test
    void shouldCapAtMaxDelayBeforeFifthAttempt() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.5);

        var delay = policy.delayBefore(5, random);

        assertThat(delay).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void shouldApplyJitterMinWithZeroRandom() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(0.0);

        var delay = policy.delayBefore(2, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(24));
    }

    @Test
    void shouldApplyJitterMaxWithOneRandom() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(1.0);

        var delay = policy.delayBefore(2, random);

        assertThat(delay).isEqualTo(Duration.ofSeconds(36));
    }

    @Test
    void shouldNeverExceedMaxDelayWithJitter() {
        var policy = RetryPolicy.standard();
        var random = fixedRandom(1.0);

        var delay = policy.delayBefore(5, random);

        assertThat(delay).isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void shouldNotBeExhaustedAt5Attempts() {
        var policy = RetryPolicy.standard();

        assertThat(policy.isExhausted(5)).isFalse();
    }

    @Test
    void shouldBeExhaustedAt6Attempts() {
        var policy = RetryPolicy.standard();

        assertThat(policy.isExhausted(6)).isTrue();
    }

    @Test
    void shouldThrowWhenDelayBeforeZero() {
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
