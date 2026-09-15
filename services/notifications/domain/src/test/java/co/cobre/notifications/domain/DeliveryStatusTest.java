package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @Test
    void shouldAllowTransitionFromPendingToCompleted() {
        assertThat(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.COMPLETED)).isTrue();
    }

    @Test
    void shouldAllowTransitionFromPendingToRetrying() {
        assertThat(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.RETRYING)).isTrue();
    }

    @Test
    void shouldAllowTransitionFromPendingToFailed() {
        assertThat(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.FAILED)).isTrue();
    }

    @Test
    void shouldAllowTransitionFromRetryingToCompleted() {
        assertThat(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.COMPLETED)).isTrue();
    }

    @Test
    void shouldAllowTransitionFromRetryingToRetrying() {
        assertThat(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.RETRYING)).isTrue();
    }

    @Test
    void shouldAllowTransitionFromRetryingToFailed() {
        assertThat(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.FAILED)).isTrue();
    }

    @Test
    void shouldAllowTransitionFromFailedToPending() {
        assertThat(DeliveryStatus.FAILED.canTransitionTo(DeliveryStatus.PENDING)).isTrue();
    }

    @Test
    void shouldNotAllowTransitionFromCompletedToAny() {
        assertThat(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.PENDING)).isFalse();
        assertThat(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.RETRYING)).isFalse();
        assertThat(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.FAILED)).isFalse();
        assertThat(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.SKIPPED)).isFalse();
    }

    @Test
    void shouldNotAllowTransitionFromSkippedToAny() {
        assertThat(DeliveryStatus.SKIPPED.canTransitionTo(DeliveryStatus.PENDING)).isFalse();
        assertThat(DeliveryStatus.SKIPPED.canTransitionTo(DeliveryStatus.RETRYING)).isFalse();
        assertThat(DeliveryStatus.SKIPPED.canTransitionTo(DeliveryStatus.COMPLETED)).isFalse();
        assertThat(DeliveryStatus.SKIPPED.canTransitionTo(DeliveryStatus.FAILED)).isFalse();
    }

    @Test
    void shouldNotAllowTransitionFromPendingToPending() {
        assertThat(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.PENDING)).isFalse();
    }

    @Test
    void shouldNotAllowTransitionFromRetryingToPending() {
        assertThat(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.PENDING)).isFalse();
    }

    @Test
    void shouldNotAllowTransitionFromFailedToCompleted() {
        assertThat(DeliveryStatus.FAILED.canTransitionTo(DeliveryStatus.COMPLETED)).isFalse();
    }

    @Test
    void shouldNotAllowTransitionFromFailedToRetrying() {
        assertThat(DeliveryStatus.FAILED.canTransitionTo(DeliveryStatus.RETRYING)).isFalse();
    }

    @Test
    void shouldConsiderCompletedAsTerminal() {
        assertThat(DeliveryStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void shouldConsiderSkippedAsTerminal() {
        assertThat(DeliveryStatus.SKIPPED.isTerminal()).isTrue();
    }

    @Test
    void shouldNotConsiderFailedAsTerminal() {
        assertThat(DeliveryStatus.FAILED.isTerminal()).isFalse();
    }

    @Test
    void shouldNotConsiderPendingAsTerminal() {
        assertThat(DeliveryStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void shouldNotConsiderRetryingAsTerminal() {
        assertThat(DeliveryStatus.RETRYING.isTerminal()).isFalse();
    }
}
