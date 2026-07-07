package com.wallet.digital_wallet.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    // NEW: PDF rendering failed unexpectedly (Feature #4) — this is a real
    // server-side problem, not a bad request, so log it and return 500.
    @ExceptionHandler(StatementGenerationException.class)
    public ResponseEntity<Map<String, Object>> handleStatementGeneration(
            StatementGenerationException ex) {
        log.error("Statement generation failed", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not generate the statement. Please try again.");
    }

    // NEW: reversing a transaction that isn't SUCCESS (already reversed,
    // failed, or itself a reversal) → 409 Conflict, not a generic 500.
    @ExceptionHandler(InvalidTransactionStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransactionState(
            InvalidTransactionStateException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    // NEW: wrong/expired/exhausted transfer OTP → 400. The client should
    // show the message and let the user retry or re-initiate.
    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOtp(
            InvalidOtpException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // NEW: expired/unknown/revoked refresh token → 403. The client should
    // treat this as "log in again", not retry.
    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRefresh(
            TokenRefreshException ex) {
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

    // Catches anything else unexpected.
    // We log the full exception server-side (so we can actually debug it)
    // but return a generic message to the client — exposing ex.getMessage()
    // directly to callers can leak internal details (SQL constraint names,
    // stack info, etc.) that an attacker could use to map out the system.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again.");
    }
}