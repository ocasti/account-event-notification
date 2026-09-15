package co.cobre.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Configuration properties for the event simulator.
 */
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private Resource eventsFile;
    private Duration emitInterval;
    private boolean emitReferenceOnStart;
    private String queueName;

    public SimulatorProperties() {
    }

    public SimulatorProperties(Resource eventsFile, Duration emitInterval, boolean emitReferenceOnStart, String queueName) {
        this.eventsFile = eventsFile;
        this.emitInterval = emitInterval;
        this.emitReferenceOnStart = emitReferenceOnStart;
        this.queueName = queueName;
    }

    public Resource eventsFile() {
        return eventsFile;
    }

    public void setEventsFile(Resource eventsFile) {
        this.eventsFile = eventsFile;
    }

    public Duration emitInterval() {
        return emitInterval;
    }

    public void setEmitInterval(Duration emitInterval) {
        this.emitInterval = emitInterval;
    }

    public boolean emitReferenceOnStart() {
        return emitReferenceOnStart;
    }

    public void setEmitReferenceOnStart(boolean emitReferenceOnStart) {
        this.emitReferenceOnStart = emitReferenceOnStart;
    }

    public String queueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }
}
