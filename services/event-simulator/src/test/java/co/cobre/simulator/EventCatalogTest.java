package co.cobre.simulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EventCatalogTest {

    private JsonMapper mapper;
    private SimulatorProperties properties;
    private EventCatalog catalog;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        properties = new SimulatorProperties(
            new ClassPathResource("notification_events.json"),
            null,
            false,
            "test-queue"
        );
        catalog = new EventCatalog(properties, mapper);
    }

    @Test
    void shouldLoadAllTenReferenceEventsWhenEventCatalogInitializedFromJson() {
        List<ReferenceEvent> events = catalog.all();

        assertThat(events).hasSize(10);
    }

    @Test
    void shouldReturnSequentialReferenceEventIdsWhenEventCatalogListsAll() {
        List<ReferenceEvent> events = catalog.all();

        assertThat(events)
            .extracting(ReferenceEvent::eventId)
            .containsExactly("EVT001", "EVT002", "EVT003", "EVT004", "EVT005",
                "EVT006", "EVT007", "EVT008", "EVT009", "EVT010");
    }

    @Test
    void shouldHaveExpectedOccurredAtWhenEvt003LoadedFromCatalog() {
        List<ReferenceEvent> events = catalog.all();

        assertThat(events)
            .filteredOn(event -> event.eventId().equals("EVT003"))
            .singleElement()
            .extracting(ReferenceEvent::occurredAt)
            .isEqualTo(Instant.parse("2024-03-15T11:20:18Z"));
    }

    @Test
    void shouldMapReferenceEventsToThreeClientIdsWhenEventCatalogLoaded() {
        List<ReferenceEvent> events = catalog.all();

        assertThat(events)
            .extracting(ReferenceEvent::clientId)
            .containsExactlyInAnyOrder(
                "CLIENT001", "CLIENT002", "CLIENT003",
                "CLIENT001", "CLIENT002", "CLIENT003",
                "CLIENT001", "CLIENT002", "CLIENT003", "CLIENT001"
            );
    }

    @Test
    void shouldReturnCatalogReferenceEventWhenPickedWithRandomGenerator() {
        RandomGenerator random = RandomGenerator.getDefault();

        ReferenceEvent picked = catalog.pick(random);

        assertThat(picked).isNotNull();
        assertThat(picked.eventId()).isIn("EVT001", "EVT002", "EVT003", "EVT004", "EVT005",
            "EVT006", "EVT007", "EVT008", "EVT009", "EVT010");
    }
}
