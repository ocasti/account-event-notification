package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventNotFoundExceptionTest {

    @Test
    void shouldIncludeEventIdInMessage() {
        var eventId = new EventId("event-404");

        var exception = new NotificationEventNotFoundException(eventId);

        assertThat(exception.getMessage()).isEqualTo("Notification event not found: event-404");
    }
}
