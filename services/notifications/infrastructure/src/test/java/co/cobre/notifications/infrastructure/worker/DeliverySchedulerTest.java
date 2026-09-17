package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverySchedulerTest {

    @Mock
    private ProcessDueDeliveries processDueDeliveries;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private DeliveryScheduler scheduler;

    static Stream<Arguments> batchFailures() {
        return Stream.of(
            Arguments.of(new RuntimeException("database error")),
            Arguments.of(new IllegalStateException("state error")),
            Arguments.of(new CompletionException(new IllegalStateException("async failure")))
        );
    }

    @Test
    void shouldRecordBatchSizeWhenProcessingSucceeds() {
        when(processDueDeliveries.processBatch()).thenReturn(42);

        scheduler.tick();

        verify(processDueDeliveries).processBatch();
        verify(metrics).batchProcessed(42);
        verifyNoMoreInteractions(metrics);
    }

    @ParameterizedTest(name = "{0} is swallowed and counted as a scheduler error")
    @MethodSource("batchFailures")
    void shouldRecordSchedulerErrorWithoutPropagatingWhenBatchThrows(RuntimeException failure) {
        when(processDueDeliveries.processBatch()).thenThrow(failure);

        scheduler.tick();

        verify(processDueDeliveries).processBatch();
        verify(metrics).schedulerError();
        verifyNoMoreInteractions(metrics);
    }
}
