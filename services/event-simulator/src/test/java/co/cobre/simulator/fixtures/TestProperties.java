package co.cobre.simulator.fixtures;

import co.cobre.simulator.SimulatorProperties;

import java.time.Duration;

public final class TestProperties {

    public static SimulatorProperties defaults() {
        return new SimulatorProperties(
            null,
            Duration.ofSeconds(2),
            true,
            "test-queue"
        );
    }

    public static SimulatorProperties withEmissionActive(boolean emitReferenceOnStart) {
        return new SimulatorProperties(
            null,
            Duration.ofSeconds(2),
            emitReferenceOnStart,
            "test-queue"
        );
    }

    private TestProperties() {
    }
}
