package co.cobre.simulator;

import co.cobre.simulator.fixtures.ReferenceEvents;
import co.cobre.simulator.fixtures.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Captor
    private ArgumentCaptor<ReferenceEvent> eventCaptor;

    @Test
    void shouldPublishAllTenReferenceEventsWhenEmissionSchedulerEmitsReferenceOnActiveStart() {
        when(catalog.all()).thenReturn(ReferenceEvents.all());
        var scheduler = new EmissionScheduler(catalog, generator, publisher, TestProperties.withEmissionActive(true));

        scheduler.emitReference();

        verify(publisher, times(10)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(ReferenceEvents.all());
    }

    @Test
    void shouldPublishNothingWhenEmissionSchedulerEmitsReferenceWhileInactive() {
        var scheduler = new EmissionScheduler(catalog, generator, publisher, TestProperties.withEmissionActive(false));

        scheduler.emitReference();

        verify(publisher, never()).publish(eventCaptor.capture());
    }

    @Test
    void shouldPublishOneDerivedEventWhenEmissionSchedulerEmitsDerived() {
        ReferenceEvent derived = ReferenceEvents.evt001();
        when(generator.derive()).thenReturn(derived);
        var scheduler = new EmissionScheduler(catalog, generator, publisher, TestProperties.withEmissionActive(true));

        scheduler.emitDerived();

        verify(publisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(derived);
    }
}
