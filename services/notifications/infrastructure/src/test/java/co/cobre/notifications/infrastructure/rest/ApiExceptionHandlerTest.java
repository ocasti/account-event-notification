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
    void shouldReturn404WhenNotificationEventNotFound() {
        var exception = new NotificationEventNotFoundException(new EventId("evt-1"));

        var response = handler.handleNotFound(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("not_found");
    }

    @Test
    void shouldReturn409WhenHandlerReceivesReplayNotAllowed() {
        var exception = new ReplayNotAllowedException(new EventId("evt-1"), DeliveryStatus.COMPLETED);

        var response = handler.handleReplayNotAllowed(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("replay_not_allowed");
    }

    @Test
    void shouldReturn409WhenStateTransitionIsIllegal() {
        var exception = new IllegalStateTransitionException(DeliveryStatus.COMPLETED, DeliveryStatus.PENDING);

        var response = handler.handleIllegalStateTransition(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("illegal_transition");
    }

    @Test
    void shouldReturn400WhenArgumentIsIllegal() {
        var exception = new IllegalArgumentException("bad value");

        var response = handler.handleIllegalArgument(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("invalid_parameter");
    }

    @Test
    void shouldReturn400WhenConstraintIsViolated() {
        var exception = new ConstraintViolationException(Set.of());

        var response = handler.handleConstraintViolation(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("validation_error");
    }
}
