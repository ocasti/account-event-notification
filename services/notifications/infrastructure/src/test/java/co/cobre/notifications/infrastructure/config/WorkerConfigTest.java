package co.cobre.notifications.infrastructure.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerConfigTest {

    private final WorkerConfig config = new WorkerConfig();

    @ParameterizedTest(name = "{0}")
    @MethodSource("workerPropertiesRows")
    void shouldCopyPropertiesValuesWhenBuildingDeliveryWorkerSettings(
        String rowName, String workerId, int batchSize, int maxPerClient, Duration lease
    ) {
        var properties = new WorkerProperties(workerId, batchSize, maxPerClient, lease);

        var settings = config.deliveryWorkerSettings(properties);

        assertThat(settings.workerId()).isEqualTo(workerId);
        assertThat(settings.batchSize()).isEqualTo(batchSize);
        assertThat(settings.maxPerClient()).isEqualTo(maxPerClient);
        assertThat(settings.lease()).isEqualTo(lease);
    }

    private static Stream<Arguments> workerPropertiesRows() {
        return Stream.of(
            Arguments.of("worker-1 with 30 second lease", "worker-1", 100, 10, Duration.ofSeconds(30)),
            Arguments.of("worker-42 with 1 minute lease", "worker-42", 50, 5, Duration.ofMinutes(1))
        );
    }
}
