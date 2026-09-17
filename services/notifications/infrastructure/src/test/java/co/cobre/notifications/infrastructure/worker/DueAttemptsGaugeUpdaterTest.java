package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptJpaRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "{0} due attempts publish {1} to the gauge")
    @CsvSource({
        "0, 0",
        "42, 42",
        "2147483647, 2147483647",
        "2147483648, 2147483647"
    })
    void shouldPublishDueCountClampedToIntWhenGaugeIsRefreshed(long dueCount, int expectedGaugeValue) {
        when(attempts.countDue()).thenReturn(dueCount);

        updater.refresh();

        verify(metrics).attemptsDue(expectedGaugeValue);
    }
}
