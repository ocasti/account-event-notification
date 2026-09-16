package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.IllegalStateTransitionException;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotificationEventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationEventNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("not_found", "Event not found"));
    }

    @ExceptionHandler(ReplayNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleReplayNotAllowed(ReplayNotAllowedException ex) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse("replay_not_allowed", "Replay not allowed for this event"));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateTransition(IllegalStateTransitionException ex) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse("illegal_transition", "Illegal state transition"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("invalid_parameter", "Invalid parameter"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("validation_error", "Validation error"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("validation_error", "Validation error"));
    }
}
