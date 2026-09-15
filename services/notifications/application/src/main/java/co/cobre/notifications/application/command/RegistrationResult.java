package co.cobre.notifications.application.command;

/**
 * Result of registering a notification event.
 */
public enum RegistrationResult {
    REGISTERED,
    SKIPPED,
    DUPLICATE
}
