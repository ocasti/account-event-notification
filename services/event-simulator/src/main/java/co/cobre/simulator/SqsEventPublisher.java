package co.cobre.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes events to an SQS queue.
 */
@Component
public class SqsEventPublisher {

    private final SqsTemplate sqsTemplate;
    private final SimulatorProperties properties;
    private final ObjectMapper mapper;

    public SqsEventPublisher(SqsTemplate sqsTemplate, SimulatorProperties properties, ObjectMapper mapper) {
        this.sqsTemplate = sqsTemplate;
        this.properties = properties;
        this.mapper = mapper;
    }

    public void publish(ReferenceEvent event) {
        AccountEventMessage message = AccountEventMessage.from(event);
        try {
            String json = mapper.writeValueAsString(message);
            sqsTemplate.send(properties.queueName(), json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
