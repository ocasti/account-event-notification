package co.cobre.notifications.infrastructure.messaging;

import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.infrastructure.metrics.DeliveryMetrics;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class MessagingTestConfig {

    @Bean
    @Primary
    public RegisterNotificationEvent registerNotificationEvent() {
        return mock(RegisterNotificationEvent.class);
    }

    @Bean
    @Primary
    public DeliveryMetrics deliveryMetrics() {
        return mock(DeliveryMetrics.class);
    }
}
