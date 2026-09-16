package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IllegalStateTransitionExceptionTest {

    @Test
    void shouldExposeFromAndToStatuses() {
        var exception = new IllegalStateTransitionException(DeliveryStatus.COMPLETED, DeliveryStatus.PENDING);

        assertThat(exception.from()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(exception.to()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(exception.getMessage()).isEqualTo("Illegal transition from COMPLETED to PENDING");
    }
}
