package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.exception.IllegalStateTransitionException;
import co.cobre.notifications.domain.exception.NotificationEventNotFoundException;
import co.cobre.notifications.domain.exception.ReplayNotAllowedException;
import co.cobre.notifications.infrastructure.rest.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for REST API.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Handles notification event not found exceptions.
     */
    @ExceptionHandler(NotificationEventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationEventNotFoundException ex) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Handles replay not allowed exceptions.
     */
    @ExceptionHandler(ReplayNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleReplayNotAllowed(ReplayNotAllowedException ex) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Handles illegal state transition exceptions.
     */
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateTransition(IllegalStateTransitionException ex) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Handles method argument validation exceptions.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Handles constraint violation exceptions.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        throw new UnsupportedOperationException("not implemented");
    }
}
