package co.cobre.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Loads and provides access to the reference event catalog.
 */
@Component
public class EventCatalog {

    private final SimulatorProperties properties;
    private final ObjectMapper mapper;

    public EventCatalog(SimulatorProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<ReferenceEvent> all() {
        throw new UnsupportedOperationException("not implemented");
    }

    public ReferenceEvent pick(RandomGenerator random) {
        throw new UnsupportedOperationException("not implemented");
    }
}
