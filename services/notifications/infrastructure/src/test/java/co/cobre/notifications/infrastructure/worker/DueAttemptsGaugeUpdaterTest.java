package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DueAttemptsGaugeUpdaterTest {

    @Mock
    private DeliveryAttemptJpaRepository attempts;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private DueAttemptsGaugeUpdater updater;

    @Test
    void refresh_publishesDueAttemptsCountToGauge() {
        when(attempts.countDue()).thenReturn(42L);

        updater.refresh();

        verify(metrics).attemptsDue(42);
    }

    @Test
    void refresh_castsDueLongToInt() {
        when(attempts.countDue()).thenReturn(Integer.MAX_VALUE + 1L);

        updater.refresh();

        verify(metrics).attemptsDue((int) Math.min(Integer.MAX_VALUE + 1L, Integer.MAX_VALUE));
    }
}
