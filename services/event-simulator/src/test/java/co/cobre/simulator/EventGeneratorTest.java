package co.cobre.simulator;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EventGeneratorTest {

    private final EventCatalog catalog = Mockito.mock(EventCatalog.class);
    private final RandomGenerator random = RandomGenerator.of("Random");
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), ZoneId.of("UTC"));
    private final EventGenerator generator = new EventGenerator(catalog, random, clock);

    @Test
    void derivePreservesClientTypeAndContent() {
        ReferenceEvent template = new ReferenceEvent(
            "EVT001", "credit_card_payment", "CLIENT001",
            "Test payment", Instant.parse("2024-03-15T09:30:22Z")
        );
        Mockito.when(catalog.pick(random)).thenReturn(template);

        ReferenceEvent derived = generator.derive();

        assertThat(derived.clientId()).isEqualTo("CLIENT001");
        assertThat(derived.eventType()).isEqualTo("credit_card_payment");
        assertThat(derived.content()).isEqualTo("Test payment");
    }

    @Test
    void deriveGeneratesNewEventIdStartingWithEvtPrefix() {
        ReferenceEvent template = new ReferenceEvent(
            "EVT001", "credit_card_payment", "CLIENT001",
            "Test payment", Instant.parse("2024-03-15T09:30:22Z")
        );
        Mockito.when(catalog.pick(random)).thenReturn(template);

        ReferenceEvent derived = generator.derive();

        assertThat(derived.eventId()).startsWith("EVT-");
        assertThat(derived.eventId()).hasSize(12);
    }

    @Test
    void deriveOccurredAtUsesClockTime() {
        ReferenceEvent template = new ReferenceEvent(
            "EVT001", "credit_card_payment", "CLIENT001",
            "Test payment", Instant.parse("2024-03-15T09:30:22Z")
        );
        Mockito.when(catalog.pick(random)).thenReturn(template);

        ReferenceEvent derived = generator.derive();

        assertThat(derived.occurredAt()).isEqualTo(Instant.parse("2025-01-15T10:00:00Z"));
    }

    @Test
    void twoConsecutiveDerivesProduceDifferentEventIds() {
        ReferenceEvent template = new ReferenceEvent(
            "EVT001", "credit_card_payment", "CLIENT001",
            "Test payment", Instant.parse("2024-03-15T09:30:22Z")
        );
        Mockito.when(catalog.pick(random)).thenReturn(template);

        ReferenceEvent derived1 = generator.derive();
        ReferenceEvent derived2 = generator.derive();

        assertThat(derived1.eventId()).isNotEqualTo(derived2.eventId());
    }

    @Test
    void fromRequestRespectAllThreeValuesAndUsesClock() {
        ReferenceEvent event = generator.fromRequest("CLIENT456", "account.updated", "Account updated");

        assertThat(event.clientId()).isEqualTo("CLIENT456");
        assertThat(event.eventType()).isEqualTo("account.updated");
        assertThat(event.content()).isEqualTo("Account updated");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2025-01-15T10:00:00Z"));
    }

    @Test
    void fromRequestGeneratesEventIdWithEvtPrefix() {
        ReferenceEvent event = generator.fromRequest("CLIENT456", "account.updated", "Account updated");

        assertThat(event.eventId()).startsWith("EVT-");
        assertThat(event.eventId()).hasSize(12);
    }
}
