package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListRequestTest {

    @Test
    void status_withFailed_returnsFailed() {
        var request = new ListRequest(
            Optional.empty(),
            Optional.empty(),
            Optional.of("failed"),
            20,
            Optional.empty()
        );

        assertThat(request.status()).contains(DeliveryStatus.FAILED);
    }

    @Test
    void status_withAbsent_returnsEmpty() {
        var request = new ListRequest(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            20,
            Optional.empty()
        );

        assertThat(request.status()).isEmpty();
    }

    @Test
    void status_withBogus_throwsException() {
        var request = new ListRequest(
            Optional.empty(),
            Optional.empty(),
            Optional.of("bogus"),
            20,
            Optional.empty()
        );

        assertThatThrownBy(request::status)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid delivery status: bogus");
    }

    @Test
    void status_withCaseMismatch_convertsToUppercase() {
        var request = new ListRequest(
            Optional.empty(),
            Optional.empty(),
            Optional.of("COMPLETED"),
            20,
            Optional.empty()
        );

        assertThat(request.status()).contains(DeliveryStatus.COMPLETED);
    }
}
