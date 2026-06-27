package com.wallet.digital_wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice  // intercepts ALL exceptions thrown anywhere in the app
public class GlobalExceptionHandler {

    // Builds a clean JSON error response
    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String message) {

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        return new ResponseEntity<>(error, status);
    }

    // NEW: catches @Valid failures and returns structured field-level error map.
    // Without this, MethodArgumentNotValidException would fall into the generic
    // Exception handler below and return an unhelpful 500 with Spring's internal
    // message. This returns 400 with exactly which fields failed and why.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field errors into a map: { "fieldName": "error message" }
        // If one field has multiple violations, the last one wins (acceptable
        // for a student project; production systems often collect all of them).
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value",
                        // merge function: if same field has multiple violations,
                        // concatenate both messages
                        (existing, replacement) -> existing + "; " + replacement
                ));

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("error", "Validation Failed");
        error.put("errors", fieldErrors);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // NEW: catches Hibernate/JPA version conflicts → 409 Conflict
    // ObjectOptimisticLockingFailureException is Spring's wrapper around
    // JPA's OptimisticLockException. It fires when walletRepository.save()
    // runs UPDATE ... WHERE version = ? and gets 0 rows affected.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {
        return buildError(HttpStatus.CONFLICT,
                "This transaction could not be completed due to a concurrent update. " +
                        "Please try again.");
    }

    // Catches our own OptimisticLockException (wrapped by the service layer)
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleCustomOptimisticLock(
            OptimisticLockException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(
            InsufficientFundsException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(WalletFrozenException.class)
    public ResponseEntity<Map<String, Object>> handleFrozenWallet(
            WalletFrozenException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            DuplicateResourceException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    // NEW: ownership/authorization failures -> 403, not a generic 500
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccess(
            UnauthorizedAccessException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // NEW: wrong username/password at login -> 401, not a generic 500
    // (this fixes the bug flagged at the end of Task 2)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Catches anything else unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong: " + ex.getMessage());
    }
}