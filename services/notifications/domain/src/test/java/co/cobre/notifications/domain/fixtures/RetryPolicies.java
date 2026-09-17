package co.cobre.notifications.domain.fixtures;

import co.cobre.notifications.domain.RetryPolicy;

import java.time.Duration;

/**
 * Object Mother for {@link RetryPolicy}. {@link #local()} mirrors
 * {@code application-local.yaml}'s {@code notifications.retry} settings; the domain has no
 * factory for it, so it is built with the constructor directly.
 */
public final class RetryPolicies {

    private RetryPolicies() {
    }

    public static RetryPolicy standard() {
        return RetryPolicy.standard();
    }

    public static RetryPolicy local() {
        return new RetryPolicy(Duration.ofSeconds(2), 4.0, Duration.ofSeconds(10), 0.2, 5);
    }
}
