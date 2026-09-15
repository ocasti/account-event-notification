package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.domain.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryConfigTest {

    @Test
    void shouldBuildRetryPolicyFromProperties() {
        var props = new RetryProperties(
            Duration.ofSeconds(30),
            4.0,
            Duration.ofMinutes(15),
            0.2,
            5
        );
        var config = new RetryConfig();

        var policy = config.retryPolicy(props);

        assertThat(policy.baseDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.factor()).isEqualTo(4.0);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.jitterRatio()).isEqualTo(0.2);
        assertThat(policy.maxAttempts()).isEqualTo(5);
    }

    @Test
    void shouldSupportFastRetryPolicyForLocalProfile() {
        var props = new RetryProperties(
            Duration.ofSeconds(2),
            4.0,
            Duration.ofSeconds(10),
            0.2,
            5
        );
        var config = new RetryConfig();

        var policy = config.retryPolicy(props);

        assertThat(policy.baseDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldHaveStandardRetryPolicyInDomain() {
        var standard = RetryPolicy.standard();

        assertThat(standard.baseDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(standard.factor()).isEqualTo(4.0);
        assertThat(standard.maxDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(standard.jitterRatio()).isEqualTo(0.2);
        assertThat(standard.maxAttempts()).isEqualTo(5);
    }
}
