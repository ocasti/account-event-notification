package co.cobre.notifications.infrastructure.metrics;

import co.cobre.notifications.infrastructure.persistence.jpa.DeliveryAttemptJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class DueAttemptsGaugeUpdater {

    private final DeliveryAttemptJpaRepository attempts;
    private final DeliveryMetrics metrics;

    public DueAttemptsGaugeUpdater(DeliveryAttemptJpaRepository attempts, DeliveryMetrics metrics) {
        this.attempts = attempts;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${notifications.worker.gauge-interval:5s}")
    public void refresh() {
        long due = attempts.countDue();
        metrics.attemptsDue((int) Math.min(due, Integer.MAX_VALUE));
    }
}
