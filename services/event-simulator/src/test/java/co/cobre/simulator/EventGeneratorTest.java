package co.cobre.simulator;

import co.cobre.simulator.fixtures.Clocks;
import co.cobre.simulator.fixtures.ReferenceEvents;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EventGeneratorTest {

    private final EventCatalog catalog = Mockito.mock(EventCatalog.class);
    private final RandomGenerator random = RandomGenerator.of("Random");
    private final EventGenerator generator = new EventGenerator(catalog, random, Clocks.fixed());

    @Test
    void shouldPreserveClientTypeAndContentWhenEventGeneratorDerivesFromTemplate() {
        Mockito.when(catalog.pick(random)).thenReturn(ReferenceEvents.evt001());

        ReferenceEvent derived = generator.derive();

        assertThat(derived.clientId()).isEqualTo("CLIENT001");
        assertThat(derived.eventType()).isEqualTo("credit_card_payment");
        assertThat(derived.content()).isEqualTo("Credit card payment received for $150.00");
    }

    @Test
    void shouldGenerateEventIdWithEvtPrefixWhenEventGeneratorDerives() {
        Mockito.when(catalog.pick(random)).thenReturn(ReferenceEvents.evt001());

        ReferenceEvent derived = generator.derive();

        assertThat(derived.eventId()).startsWith("EVT-");
        assertThat(derived.eventId()).hasSize(12);
    }

    @Test
    void shouldSetOccurredAtFromClockWhenEventGeneratorDerives() {
        Mockito.when(catalog.pick(random)).thenReturn(ReferenceEvents.evt001());

        ReferenceEvent derived = generator.derive();

        assertThat(derived.occurredAt()).isEqualTo(Clocks.NOW);
    }

    @Test
    void shouldGenerateDifferentEventIdsWhenEventGeneratorDerivesTwiceConsecutively() {
        Mockito.when(catalog.pick(random)).thenReturn(ReferenceEvents.evt001());

        ReferenceEvent initialDerivation = generator.derive();
        ReferenceEvent subsequentDerivation = generator.derive();

        assertThat(initialDerivation.eventId()).isNotEqualTo(subsequentDerivation.eventId());
    }

    @Test
    void shouldBuildReferenceEventFromRequestValuesWhenEventGeneratorFromRequestCalled() {
        ReferenceEvent event = generator.fromRequest("CLIENT456", "account.updated", "Account updated");

        assertThat(event.clientId()).isEqualTo("CLIENT456");
        assertThat(event.eventType()).isEqualTo("account.updated");
        assertThat(event.content()).isEqualTo("Account updated");
        assertThat(event.occurredAt()).isEqualTo(Clocks.NOW);
    }

    @Test
    void shouldGenerateEventIdWithEvtPrefixWhenEventGeneratorBuildsFromRequest() {
        ReferenceEvent event = generator.fromRequest("CLIENT456", "account.updated", "Account updated");

        assertThat(event.eventId()).startsWith("EVT-");
        assertThat(event.eventId()).hasSize(12);
    }
}
