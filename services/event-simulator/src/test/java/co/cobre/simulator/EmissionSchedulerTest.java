package co.cobre.simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmissionSchedulerTest {

    @Mock
    private EventCatalog catalog;

    @Mock
    private EventGenerator generator;

    @Mock
    private SqsEventPublisher publisher;

    @Test
    void emitReferencePublishesAllTenWhenActiveOnStart() {
        SimulatorProperties properties = new SimulatorProperties(
            null,
            Duration.ofSeconds(2),
            true,
            "test-queue"
        );
        when(catalog.all()).thenReturn(
            java.util.List.of(
                new ReferenceEvent("EVT001", "type1", "client1", "content1", Instant.now()),
                new ReferenceEvent("EVT002", "type2", "client2", "content2", Instant.now()),
                new ReferenceEvent("EVT003", "type3", "client3", "content3", Instant.now()),
                new ReferenceEvent("EVT004", "type4", "client1", "content4", Instant.now()),
                new ReferenceEvent("EVT005", "type5", "client2", "content5", Instant.now()),
                new ReferenceEvent("EVT006", "type6", "client3", "content6", Instant.now()),
                new ReferenceEvent("EVT007", "type7", "client1", "content7", Instant.now()),
                new ReferenceEvent("EVT008", "type8", "client2", "content8", Instant.now()),
                new ReferenceEvent("EVT009", "type9", "client3", "content9", Instant.now()),
                new ReferenceEvent("EVT010", "type10", "client1", "content10", Instant.now())
            )
        );
        EmissionScheduler scheduler = new EmissionScheduler(catalog, generator, publisher, properties);

        scheduler.emitReference();

        verify(publisher, times(10)).publish(any(ReferenceEvent.class));
    }

    @Test
    void emitReferencePublishesNothingWhenInactive() {
        SimulatorProperties properties = new SimulatorProperties(
            null,
            Duration.ofSeconds(2),
            false,
            "test-queue"
        );
        EmissionScheduler scheduler = new EmissionScheduler(catalog, generator, publisher, properties);

        scheduler.emitReference();

        verify(publisher, never()).publish(any(ReferenceEvent.class));
    }

    @Test
    void emitDerivedPublishesOneEvent() {
        SimulatorProperties properties = new SimulatorProperties(
            null,
            Duration.ofSeconds(2),
            true,
            "test-queue"
        );
        ReferenceEvent derived = new ReferenceEvent("EVT-NEWID", "type", "client", "content", Instant.now());
        when(generator.derive()).thenReturn(derived);
        EmissionScheduler scheduler = new EmissionScheduler(catalog, generator, publisher, properties);

        scheduler.emitDerived();

        verify(publisher, times(1)).publish(derived);
    }
}
