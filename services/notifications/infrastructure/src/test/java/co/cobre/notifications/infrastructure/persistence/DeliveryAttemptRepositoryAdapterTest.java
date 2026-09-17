package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
        var claim = new DeliveryClaim(Instant.now(), 10, 10, "worker-1", Duration.ofSeconds(16));
        when(jpaRepository.claimDueAttempts(anyLong(), anyInt())).thenReturn(List.of(row));
        when(jpaRepository.updateClaimedBatch(anyList(), anyString())).thenReturn(0);

        var claimed = adapter.claimDue(claim);

        assertThat(claimed).isEmpty();
    }
}
