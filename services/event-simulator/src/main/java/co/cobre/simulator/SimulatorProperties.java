package co.cobre.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Configuration properties for the event simulator.
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
    Resource eventsFile,
    Duration emitInterval,
    boolean emitReferenceOnStart,
    String queueName
) {
}
