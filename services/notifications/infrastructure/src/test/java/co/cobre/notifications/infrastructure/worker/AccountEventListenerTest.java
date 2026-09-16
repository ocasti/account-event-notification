package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegisterEventCommand;
import co.cobre.notifications.application.usecase.RegistrationResult;
import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountEventListenerTest {

    @Mock
    private RegisterNotificationEvent registerNotificationEvent;

    @Mock
    private AccountEventMessageMapper mapper;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private AccountEventListener listener;

    @Test
    void onMessageRegistered() {
        var message = new AccountEventMessage(
            "EVT001",
            "account.updated",
            "CLIENT123",
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        var command = new RegisterEventCommand(
            new EventId("EVT001"),
            new ClientId("CLIENT123"),
            new EventKey("account.updated"),
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(mapper.toCommand(message)).thenReturn(command);
        when(registerNotificationEvent.register(command)).thenReturn(RegistrationResult.REGISTERED);

        listener.onMessage(message);

        verify(registerNotificationEvent).register(command);
        verify(metrics).registered("CLIENT123", "account.updated");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void onMessageSkipped() {
        var message = new AccountEventMessage(
            "EVT002",
            "account.deleted",
            "CLIENT456",
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        var command = new RegisterEventCommand(
            new EventId("EVT002"),
            new ClientId("CLIENT456"),
            new EventKey("account.deleted"),
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(mapper.toCommand(message)).thenReturn(command);
        when(registerNotificationEvent.register(command)).thenReturn(RegistrationResult.SKIPPED);

        listener.onMessage(message);

        verify(registerNotificationEvent).register(command);
        verify(metrics).skipped("CLIENT456", "account.deleted");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void onMessageDuplicate() {
        var message = new AccountEventMessage(
            "EVT003",
            "account.created",
            "CLIENT789",
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        var command = new RegisterEventCommand(
            new EventId("EVT003"),
            new ClientId("CLIENT789"),
            new EventKey("account.created"),
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(mapper.toCommand(message)).thenReturn(command);
        when(registerNotificationEvent.register(command)).thenReturn(RegistrationResult.DUPLICATE);

        listener.onMessage(message);

        verify(registerNotificationEvent).register(command);
        verify(metrics).duplicate("CLIENT789", "account.created");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void onMessageThrowsWhenRegisterFails() {
        var message = new AccountEventMessage(
            "EVT004",
            "account.suspended",
            "CLIENT999",
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        var command = new RegisterEventCommand(
            new EventId("EVT004"),
            new ClientId("CLIENT999"),
            new EventKey("account.suspended"),
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(mapper.toCommand(message)).thenReturn(command);
        when(registerNotificationEvent.register(command)).thenThrow(new RuntimeException("database error"));

        assertThrows(RuntimeException.class, () -> listener.onMessage(message));

        verify(registerNotificationEvent).register(command);
        verifyNoInteractions(metrics);
    }
}
