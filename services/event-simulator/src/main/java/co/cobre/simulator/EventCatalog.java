package co.cobre.simulator;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class EventCatalog {

    private final SimulatorProperties properties;
    private final JsonMapper mapper;
    private List<ReferenceEvent> events;

    public EventCatalog(SimulatorProperties properties, JsonMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<ReferenceEvent> all() {
        if (events == null) {
            events = loadEvents();
        }
        return events;
    }

    public ReferenceEvent pick(RandomGenerator random) {
        List<ReferenceEvent> all = all();
        int index = random.nextInt(all.size());
        return all.get(index);
    }

    private List<ReferenceEvent> loadEvents() {
        try {
            JsonNode root = mapper.readTree(properties.eventsFile().getInputStream());
            JsonNode eventsArray = root.get("events");
            List<ReferenceEvent> result = new ArrayList<>();

            for (JsonNode node : eventsArray) {
                ReferenceEvent event = new ReferenceEvent(
                    node.get("event_id").asText(),
                    node.get("event_type").asText(),
                    node.get("client_id").asText(),
                    node.get("content").asText(),
                    Instant.parse(node.get("delivery_date").asText())
                );
                result.add(event);
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load events catalog", e);
        }
    }
}
