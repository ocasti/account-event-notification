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
    void loadsAllTenEventsFromJson() {
        List<ReferenceEvent> events = catalog.all();
        assertThat(events).hasSize(10);
    }

    @Test
    void eventIdsAreSequentialEvt001ToEvt010() {
        List<ReferenceEvent> events = catalog.all();
        assertThat(events)
            .extracting(ReferenceEvent::eventId)
            .containsExactly("EVT001", "EVT002", "EVT003", "EVT004", "EVT005",
                "EVT006", "EVT007", "EVT008", "EVT009", "EVT010");
    }

    @Test
    void evt003OccurredAtIsMarc15At11_20_18Z() {
        List<ReferenceEvent> events = catalog.all();
        ReferenceEvent evt003 = events.stream()
            .filter(e -> e.eventId().equals("EVT003"))
            .findFirst()
            .orElseThrow();
        assertThat(evt003.occurredAt()).isEqualTo(Instant.parse("2024-03-15T11:20:18Z"));
    }

    @Test
    void hasThreeClientsClientTypesAsExpected() {
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
    void pickWithFixedRandomReturnsConsistentIndex() {
        RandomGenerator random = RandomGenerator.getDefault();
        ReferenceEvent picked = catalog.pick(random);
        assertThat(picked).isNotNull();
        assertThat(picked.eventId()).isIn("EVT001", "EVT002", "EVT003", "EVT004", "EVT005",
            "EVT006", "EVT007", "EVT008", "EVT009", "EVT010");
    }
}
