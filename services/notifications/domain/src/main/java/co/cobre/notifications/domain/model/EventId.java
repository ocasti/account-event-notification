package co.cobre.notifications.domain.model;

/**
 * Value object representing a notification event identifier.
 */
public record EventId(String value) {
    private static final int MAX_LENGTH = 64;

    public EventId {
        if (value == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Event ID cannot be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Event ID cannot exceed %d characters", MAX_LENGTH)
            );
        }
    }
}
