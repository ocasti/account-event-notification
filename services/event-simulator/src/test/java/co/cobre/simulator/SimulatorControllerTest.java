package co.cobre.simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatorControllerTest {

    @Mock
    private EventGenerator generator;

    @Mock
    private SqsEventPublisher publisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private SimulatorController controller;

    @Test
    void emitCallsGeneratorAndPublisher() {
        ReferenceEvent event = new ReferenceEvent("EVT-NEW123", "account.created", "CLIENT123", "Account created", Instant.now());
        when(generator.fromRequest("CLIENT123", "account.created", "Account created"))
            .thenReturn(event);

        SimulatorController.EmitRequest request = new SimulatorController.EmitRequest("CLIENT123", "account.created", "Account created");
        controller.emit(request);

        verify(generator).fromRequest("CLIENT123", "account.created", "Account created");
        verify(publisher).publish(event);
    }
}
