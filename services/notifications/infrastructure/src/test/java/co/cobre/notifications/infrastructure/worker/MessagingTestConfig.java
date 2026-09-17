package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.application.usecase.RegistrationResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class MessagingTestConfig {

    /**
     * Builds the JSON body of an account event as published by the simulator,
     * so listener tests only state the fields that identify the message.
     */
    public static String accountEventJson(String eventId, String eventType, String clientId) {
        return """
            {
                "event_id": "%s",
                "event_type": "%s",
                "client_id": "%s",
                "content": "account event",
                "occurred_at": "2025-01-01T10:00:00Z"
            }
            """.formatted(eventId, eventType, clientId);
    }

    @Bean
    @Primary
    public RegisterNotificationEvent registerNotificationEvent() {
        var mock = mock(RegisterNotificationEvent.class);
        when(mock.register(any())).thenReturn(RegistrationResult.REGISTERED);
        return mock;
    }

    @Bean
    @Primary
    public DeliveryMetrics deliveryMetrics() {
        return mock(DeliveryMetrics.class);
    }
}
