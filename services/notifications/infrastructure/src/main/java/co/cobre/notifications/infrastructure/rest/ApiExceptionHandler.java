package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.IllegalStateTransitionException;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotificationEventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ApiResponse(
        responseCode = "404",
        description = "No event with this ID for the authenticated client",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ErrorResponse handleNotFound(NotificationEventNotFoundException ex) {
        return new ErrorResponse("not_found", "Event not found");
    }

    @ExceptionHandler(ReplayNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ApiResponse(
        responseCode = "409",
        description = "The event is not in a state that allows a replay",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ErrorResponse handleReplayNotAllowed(ReplayNotAllowedException ex) {
        return new ErrorResponse("replay_not_allowed", "Replay not allowed for this event");
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ApiResponse(
        responseCode = "409",
        description = "The event is not in a state that allows a replay",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ErrorResponse handleIllegalStateTransition(IllegalStateTransitionException ex) {
        return new ErrorResponse("illegal_transition", "Illegal state transition");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(
        responseCode = "400",
        description = "Invalid query parameter (delivery_status, from/to, limit or cursor)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse("invalid_parameter", "Invalid parameter");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(
        responseCode = "400",
        description = "Validation error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return new ErrorResponse("validation_error", "Validation error");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(
        responseCode = "400",
        description = "Validation error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        return new ErrorResponse("validation_error", "Validation error");
    }
}
