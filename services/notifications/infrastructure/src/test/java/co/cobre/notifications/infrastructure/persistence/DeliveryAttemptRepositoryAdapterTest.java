package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.Ids;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAttemptRepositoryAdapterTest {

    @Mock
    private DeliveryAttemptJpaRepository jpaRepository;

    @Mock
    private DeliveryAttemptEntityMapper mapper;

    @Test
    void shouldReturnEmptyWhenBatchUpdateClaimsNoRows() {
        var adapter = new DeliveryAttemptRepositoryAdapter(jpaRepository, mapper);
        var claimedRowId = UUID.randomUUID();
        var row = Map.<String, Object>of("id", claimedRowId, "client_id", "CLIENT_001");
        var claim = new DeliveryClaim(Clocks.NOW, 10, 10, Ids.WORKER_1, Duration.ofSeconds(16));
        when(jpaRepository.claimDueAttempts(16L, 10)).thenReturn(List.of(row));
        when(jpaRepository.updateClaimedBatch(List.of(claimedRowId), Ids.WORKER_1)).thenReturn(0);

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).isEmpty();
    }
}
