package co.cobre.notifications.domain.fixtures;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Fixed points in time for tests. Use {@link #fixed()} as the {@link Clock} passed into use
 * cases so every "now" in production code resolves to {@link #NOW}, and derive expected
 * timestamps from {@link #NOW} rather than calling {@code Instant.now()}.
 */
public final class Clocks {

    public static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private Clocks() {
    }

    public static Clock fixed() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static Clock fixedAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
