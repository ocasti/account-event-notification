package co.cobre.notifications.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class WorkerConfigTest {

    private final WorkerConfig config = new WorkerConfig();

    @Test
    void deliveryWorkerSettingsCopiesPropertiesValues() {
        var properties = new WorkerProperties(
            "worker-1",
            100,
            10,
            Duration.ofSeconds(30)
        );

        var settings = config.deliveryWorkerSettings(properties);

        assertEquals("worker-1", settings.workerId());
        assertEquals(100, settings.batchSize());
        assertEquals(10, settings.maxPerClient());
        assertEquals(Duration.ofSeconds(30), settings.lease());
    }

    @Test
    void deliveryWorkerSettingsWithDifferentValues() {
        var properties = new WorkerProperties(
            "worker-42",
            50,
            5,
            Duration.ofMinutes(1)
        );

        var settings = config.deliveryWorkerSettings(properties);

        assertEquals("worker-42", settings.workerId());
        assertEquals(50, settings.batchSize());
        assertEquals(5, settings.maxPerClient());
        assertEquals(Duration.ofMinutes(1), settings.lease());
    }
}
