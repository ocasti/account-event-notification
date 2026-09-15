package co.cobre.notifications.domain.model;

/**
 * Value object representing a client identifier.
 */
public record ClientId(String value) {
    private static final int MAX_LENGTH = 64;

    public ClientId {
        if (value == null) {
            throw new IllegalArgumentException("Client ID cannot be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Client ID cannot be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Client ID cannot exceed %d characters", MAX_LENGTH)
            );
        }
    }
}
