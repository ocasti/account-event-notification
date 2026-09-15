package co.cobre.simulator;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes events to an SQS queue.
 */
@Component
public class SqsEventPublisher {

    private final SqsTemplate sqsTemplate;
    private final SimulatorProperties properties;

    public SqsEventPublisher(SqsTemplate sqsTemplate, SimulatorProperties properties) {
        this.sqsTemplate = sqsTemplate;
        this.properties = properties;
    }

    public void publish(ReferenceEvent event) {
        throw new UnsupportedOperationException("not implemented");
    }
}
