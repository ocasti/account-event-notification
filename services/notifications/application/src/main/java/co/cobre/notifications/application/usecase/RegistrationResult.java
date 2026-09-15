package co.cobre.notifications.application.usecase;

/**
 * Result of registering a notification event.
 */
public enum RegistrationResult {
    REGISTERED,
    SKIPPED,
    DUPLICATE
}
