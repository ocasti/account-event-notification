package co.cobre.simulator;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.random.RandomGenerator;

@Component
public class EventGenerator {

    private static final int EVENT_ID_HEX_DIGITS = 8;
    private static final int HEX_RADIX = 16;

    private final EventCatalog catalog;
    private final RandomGenerator random;
    private final Clock clock;

    public EventGenerator(EventCatalog catalog, RandomGenerator random, Clock clock) {
        this.catalog = catalog;
        this.random = random;
        this.clock = clock;
    }

    public ReferenceEvent derive() {
        ReferenceEvent template = catalog.pick(random);
        return new ReferenceEvent(
            generateNewEventId(),
            template.eventType(),
            template.clientId(),
            template.content(),
            clock.instant()
        );
    }

    public ReferenceEvent fromRequest(String clientId, String eventType, String content) {
        return new ReferenceEvent(
            generateNewEventId(),
            eventType,
            clientId,
            content,
            clock.instant()
        );
    }

    private String generateNewEventId() {
        StringBuilder id = new StringBuilder("EVT-");
        for (int i = 0; i < EVENT_ID_HEX_DIGITS; i++) {
            int digit = random.nextInt(HEX_RADIX);
            id.append(String.format("%X", digit));
        }
        return id.toString();
    }
}
