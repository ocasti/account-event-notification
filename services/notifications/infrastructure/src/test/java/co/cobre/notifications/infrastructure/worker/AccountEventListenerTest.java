package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegisterEventCommand;
import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.application.usecase.RegistrationResult;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountEventListenerTest {

    private static final String EVENT_ID = "EVT001";
    private static final String CLIENT_ID = "CLIENT123";
    private static final String EVENT_KEY = "account.updated";
    private static final Instant OCCURRED_AT = Instant.parse("2025-01-01T10:00:00Z");

    @Mock
    private RegisterNotificationEvent registerNotificationEvent;

    @Mock
    private AccountEventMessageMapper mapper;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private AccountEventListener listener;

    /**
     * One row per registration result: the result the use case returns and the
     * metric the listener must record for it, expressed as the call made on a
     * {@code verify(metrics)} proxy.
     */
    static Stream<Arguments> registrationOutcomes() {
        return Stream.of(
            Arguments.of(RegistrationResult.REGISTERED, "registered",
                (BiConsumer<DeliveryMetrics, String[]>) (m, tags) -> m.registered(tags[0], tags[1])),
            Arguments.of(RegistrationResult.SKIPPED, "skipped",
                (BiConsumer<DeliveryMetrics, String[]>) (m, tags) -> m.skipped(tags[0], tags[1])),
            Arguments.of(RegistrationResult.DUPLICATE, "duplicate",
                (BiConsumer<DeliveryMetrics, String[]>) (m, tags) -> m.duplicate(tags[0], tags[1]))
        );
    }

    @ParameterizedTest(name = "{0} records the {1} metric")
    @MethodSource("registrationOutcomes")
    void shouldRecordMatchingMetricWhenRegistrationReturnsResult(
        RegistrationResult result, String metricName, BiConsumer<DeliveryMetrics, String[]> expectedMetric
    ) {
        var message = accountEventMessage();
        var command = registerCommand();
        when(mapper.toCommand(message)).thenReturn(command);
        when(registerNotificationEvent.register(command)).thenReturn(result);

        listener.onMessage(message);

        verify(registerNotificationEvent).register(command);
        expectedMetric.accept(verify(metrics), new String[] {CLIENT_ID, EVENT_KEY});
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void shouldPropagateExceptionWhenRegistrationFails() {
        var message = accountEventMessage();
        var command = registerCommand();
        when(mapper.toCommand(message)).thenReturn(command);
        when(registerNotificationEvent.register(command)).thenThrow(new RuntimeException("database error"));

        assertThatThrownBy(() -> listener.onMessage(message))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("database error");

        verify(registerNotificationEvent).register(command);
        verifyNoInteractions(metrics);
    }

    private static AccountEventMessage accountEventMessage() {
        return new AccountEventMessage(EVENT_ID, EVENT_KEY, CLIENT_ID, "content", OCCURRED_AT);
    }

    private static RegisterEventCommand registerCommand() {
        return new RegisterEventCommand(
            new EventId(EVENT_ID),
            new ClientId(CLIENT_ID),
            new EventKey(EVENT_KEY),
            "content",
            OCCURRED_AT
        );
    }
}
