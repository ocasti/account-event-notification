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
        return ResponseEntity.status(404)
            .body(new ErrorResponse("not_found", "Event not found"));
    }

    /**
     * Handles replay not allowed exceptions.
     */
    @ExceptionHandler(ReplayNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleReplayNotAllowed(ReplayNotAllowedException ex) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse("replay_not_allowed", "Replay not allowed for this event"));
    }

    /**
     * Handles illegal state transition exceptions.
     */
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateTransition(IllegalStateTransitionException ex) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse("illegal_transition", "Illegal state transition"));
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("invalid_parameter", "Invalid parameter"));
    }

    /**
     * Handles method argument validation exceptions.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("validation_error", "Validation error"));
    }

    /**
     * Handles constraint violation exceptions.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("validation_error", "Validation error"));
    }
}
