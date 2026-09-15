package co.cobre.simulator;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.random.RandomGenerator;

/**
 * Generates derived events from the catalog.
 */
@Component
public class EventGenerator {

    private final EventCatalog catalog;
    private final RandomGenerator random;
    private final Clock clock;

    public EventGenerator(EventCatalog catalog, RandomGenerator random, Clock clock) {
        this.catalog = catalog;
        this.random = random;
        this.clock = clock;
    }

    public ReferenceEvent derive() {
        throw new UnsupportedOperationException("not implemented");
    }

    public ReferenceEvent fromRequest(String clientId, String eventType, String content) {
        throw new UnsupportedOperationException("not implemented");
    }
}
