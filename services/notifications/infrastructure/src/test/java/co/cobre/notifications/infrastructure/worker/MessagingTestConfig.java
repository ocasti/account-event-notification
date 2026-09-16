package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegistrationResult;
import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class MessagingTestConfig {

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
