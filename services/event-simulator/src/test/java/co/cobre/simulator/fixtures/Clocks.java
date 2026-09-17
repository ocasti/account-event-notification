package co.cobre.simulator.fixtures;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public final class Clocks {

    public static final Instant NOW = Instant.parse("2025-01-15T10:00:00Z");

    public static Clock fixed() {
        return Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    private Clocks() {
    }
}
