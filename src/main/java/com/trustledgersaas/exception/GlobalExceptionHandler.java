package com.trustledgersaas.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Centralized exception handling for the entire
 * application.
 *
 * @ControllerAdvice makes this class intercept exceptions thrown by ANY
 *                   controller.
 *                   Instead of each controller handling its own errors, all
 *                   errors flow through here
 *                   for consistent, clean error responses.
 *
 *                   Key security rule (from Section 3):
 *                   BadCredentialsException MUST return a GENERIC message
 *                   "Invalid email or password" —
 *                   never reveal whether the email or the password was
 *                   specifically wrong, to avoid
 *                   leaking information about which emails exist in the system.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles BadCredentialsException — thrown when login password doesn't match.
     *
     * SECURITY: Always returns the generic message "Invalid email or password".
     * Never say "email not found" or "wrong password" separately.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Failed login attempt: {}", ex.getMessage());
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid email or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handles LockedException — thrown when an account is locked due to too many
     * failed attempts.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, String>> handleLockedException(LockedException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.LOCKED).body(response);
    }

    /**
     * Handles ResourceNotFoundException — thrown when a requested entity doesn't
     * exist.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles InvalidRequestException — thrown for business logic violations
     * (e.g. trying to backdate a loan, exceeding customer limit on Basic plan).
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles DTO validation errors (from @Valid + Bean Validation annotations).
     * Returns a map of field names → error messages so the frontend can show
     * per-field validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Handles file upload size exceeded errors.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "File size exceeds maximum limit of 10MB. Please upload a smaller file.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Catch-all handler for any unexpected exceptions.
     * Logs the full stack trace for debugging but returns a generic message to the
     * user.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        Map<String, String> response = new HashMap<>();
        response.put("error", "An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
