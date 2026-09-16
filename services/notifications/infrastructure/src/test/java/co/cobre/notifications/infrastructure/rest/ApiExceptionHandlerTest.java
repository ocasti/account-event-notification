package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.IllegalStateTransitionException;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handlesNotificationEventNotFound() {
        var response = handler.handleNotFound(new NotificationEventNotFoundException(new EventId("evt-1")));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("not_found");
    }

    @Test
    void handlesReplayNotAllowed() {
        var response = handler.handleReplayNotAllowed(
            new ReplayNotAllowedException(new EventId("evt-1"), DeliveryStatus.COMPLETED)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("replay_not_allowed");
    }

    @Test
    void handlesIllegalStateTransition() {
        var response = handler.handleIllegalStateTransition(
            new IllegalStateTransitionException(DeliveryStatus.COMPLETED, DeliveryStatus.PENDING)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("illegal_transition");
    }

    @Test
    void handlesIllegalArgument() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad value"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("invalid_parameter");
    }

    @Test
    void handlesConstraintViolation() {
        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("validation_error");
    }
}
