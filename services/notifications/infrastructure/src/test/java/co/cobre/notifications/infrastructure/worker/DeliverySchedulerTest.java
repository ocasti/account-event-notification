package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliverySchedulerTest {

    @Mock
    private ProcessDueDeliveries processDueDeliveries;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private DeliveryScheduler scheduler;

    @Test
    void tickProcessesBatchAndRecordsMetrics() {
        when(processDueDeliveries.processBatch()).thenReturn(42);

        scheduler.tick();

        verify(processDueDeliveries).processBatch();
        verify(metrics).batchProcessed(42);
    }

    @Test
    void tickHandlesExceptionWithoutPropagating() {
        when(processDueDeliveries.processBatch()).thenThrow(new RuntimeException("database error"));

        scheduler.tick();

        verify(processDueDeliveries).processBatch();
        verify(metrics).schedulerError();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void tickRecordsErrorMetricOnException() {
        when(processDueDeliveries.processBatch()).thenThrow(new IllegalStateException("state error"));

        scheduler.tick();

        verify(metrics).schedulerError();
    }
}
