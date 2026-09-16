package co.cobre.simulator;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmissionScheduler {

    private final EventCatalog catalog;
    private final EventGenerator generator;
    private final SqsEventPublisher publisher;
    private final SimulatorProperties properties;

    public EmissionScheduler(
        EventCatalog catalog,
        EventGenerator generator,
        SqsEventPublisher publisher,
        SimulatorProperties properties
    ) {
        this.catalog = catalog;
        this.generator = generator;
        this.publisher = publisher;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void emitReference() {
        if (properties.emitReferenceOnStart()) {
            for (ReferenceEvent event : catalog.all()) {
                publisher.publish(event);
            }
        }
    }

    @Scheduled(fixedDelayString = "${simulator.emit-interval:2s}", initialDelayString = "${simulator.emit-interval:2s}")
    public void emitDerived() {
        ReferenceEvent derived = generator.derive();
        publisher.publish(derived);
    }
}
